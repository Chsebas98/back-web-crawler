# Cómo debe consumir el frontend esta API

Guía rápida para el proyecto frontend (React + TS + Vite): base URL, endpoint,
parámetros, forma exacta de la respuesta y manejo de errores. Todo lo de aquí
está sacado directamente del código actual del backend (no es una propuesta,
es lo que hoy responde el servidor).

## 1. Base URL

```
http://localhost:8081
```

El puerto está fijo en `application.properties` (`server.port=8081`). Si el
backend corre en otra máquina/puerto, ajustarlo ahí en vez de asumir 8080.

## 2. CORS

El backend solo permite el/los orígenes configurados en `CORS_ALLOWED_ORIGINS`
(por defecto `http://localhost:5173`, el puerto default de Vite). Si el
frontend corre en otro puerto (por ejemplo si Vite eligió 5174 porque 5173
estaba ocupado), hay que:

1. Ver qué origen está usando realmente el frontend (lo que muestra la
   consola de Vite al arrancar, `Local: http://localhost:XXXX`).
2. Exportar `CORS_ALLOWED_ORIGINS=http://localhost:XXXX` antes de levantar el
   backend (o agregarlo separado por comas si necesitás varios orígenes).

No hace falta ningún header de autenticación: la API no tiene auth (está
abierta a propósito, ver `SecurityConfig`).

## 3. El único endpoint

```
GET /api/stories?filter=<FILTER>
```

| Parámetro | Tipo | Requerido | Valores válidos |
|---|---|---|---|
| `filter` | query string | sí | `MORE_THAN_FIVE_WORDS` \| `FIVE_OR_FEWER_WORDS` |

**Importante:** el valor de `filter` es **case-sensitive** y debe mandarse
exactamente en mayúsculas tal cual el enum (`StoryFilter.valueOf(...)` en el
backend no acepta minúsculas). `filter=more_than_five_words` o
`filter=` (vacío) devuelven 400.

| Valor de `filter` | Qué hace |
|---|---|
| `MORE_THAN_FIVE_WORDS` | Títulos con más de 5 palabras, ordenado por `comments` descendente |
| `FIVE_OR_FEWER_WORDS` | Títulos con 5 palabras o menos, ordenado por `points` descendente |

Ejemplo real:

```
GET http://localhost:8081/api/stories?filter=MORE_THAN_FIVE_WORDS
GET http://localhost:8081/api/stories?filter=FIVE_OR_FEWER_WORDS
```

## 4. Forma de la respuesta (SIEMPRE la misma)

Toda respuesta del backend - éxito o error, incluyendo 404/405 de rutas que
ni siquiera existen - tiene exactamente esta forma. El frontend puede
parsear con un solo tipo, sin ramificar según el status HTTP:

```ts
interface ApiResponse<T> {
  response: boolean;        // true si fue exitoso, false si hubo cualquier error
  statusCode: number;       // el mismo status HTTP, repetido en el body
  message: string;          // texto corto, pensado para un título de alerta/toast
  result: T | null;         // el payload (objeto o array) en éxito, siempre null en error
  errorDetail: string | null; // descripción segura y genérica del error; null en éxito
}

interface Story {
  number: number;
  title: string;
  points: number;
  comments: number;
}
```

`result` en `/api/stories` es siempre `Story[]` cuando `response: true`.

**`errorDetail` nunca trae el mensaje real de la excepción** (para no filtrar
detalles internos) - es siempre uno de estos textos genéricos, pensados para
mostrarse directo al usuario. No parsear ni hacer lógica basada en el texto
exacto de `errorDetail`, solo mostrarlo.

## 5. Códigos de estado y cuándo aparecen

| statusCode | Cuándo | `message` | `errorDetail` |
|---|---|---|---|
| 200 | filtro válido, todo salió bien | `"Stories retrieved"` | `null` |
| 400 | falta `filter`, o no es uno de los dos valores válidos | `"Information incomplete"` | `"The information provided is incomplete or invalid. Please try again."` |
| 404 | ruta que no existe | `"Not found"` | `"The requested resource does not exist."` |
| 405 | método HTTP no soportado en esa ruta | `"Method not allowed"` | `"This operation is not supported for the requested resource."` |
| 502 | Hacker News no respondió o la página vino distinta a lo esperado | `"Information unavailable"` | `"We could not complete your request. Please try again later."` |
| 500 | cualquier otro error no anticipado | `"Something went wrong"` | `"An unexpected error occurred. Please try again later."` |

Para la UI: alcanza con leer `response` (booleano) para saber si mostrar la
lista o un estado de error, y usar `message`/`errorDetail` para el texto del
error. No hace falta un `switch` por `statusCode` salvo que se quiera un
ícono/estilo distinto para 502 vs 500 vs 400.

## 6. Ejemplo de cliente (fetch)

```ts
// src/api/storiesApi.ts
const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8081";

export type StoryFilter = "MORE_THAN_FIVE_WORDS" | "FIVE_OR_FEWER_WORDS";

export interface Story {
  number: number;
  title: string;
  points: number;
  comments: number;
}

interface ApiResponse<T> {
  response: boolean;
  statusCode: number;
  message: string;
  result: T | null;
  errorDetail: string | null;
}

export async function fetchStories(filter: StoryFilter): Promise<Story[]> {
  const res = await fetch(`${BASE_URL}/api/stories?filter=${filter}`);
  const body: ApiResponse<Story[]> = await res.json();

  if (!body.response) {
    // body.message / body.errorDetail ya vienen listos para mostrar al usuario
    throw new Error(body.errorDetail ?? body.message);
  }

  return body.result ?? [];
}
```

Notar: **siempre** se puede hacer `await res.json()` incluso si `res.ok` es
`false` - el backend nunca devuelve un body vacío o HTML de error, siempre el
mismo `ApiResponse` en JSON, sea cual sea el status.

Si el frontend usa una variable de entorno para la base URL (recomendado en
vez de hardcodear `localhost:8081`), agregar en el `.env` del frontend:

```
VITE_API_BASE_URL=http://localhost:8081
```

## 7. Errores comunes al integrar (ya vistos en este proyecto)

- **CORS bloqueado en la consola del navegador**: el origen del frontend no
  está en `CORS_ALLOWED_ORIGINS` del backend (ver sección 2).
- **400 aunque el filtro "parece" correcto**: revisar mayúsculas exactas -
  `MORE_THAN_FIVE_WORDS`, no `More_Than_Five_Words` ni `more-than-five-words`.
- **Nada responde / connection refused**: el backend no está levantado en el
  puerto 8081, o el `filter` faltante hace que el navegador ni llegue a
  mandar la query (poco probable, pero confirmar con la pestaña Network).
