import type { FastifyInstance } from "fastify";
import { withSharedApiErrorResponses } from "./error-responses.js";

export function registerOpenApiRoute(app: FastifyInstance): void {
  app.get("/api/v1/openapi.json", async () =>
    withSharedApiErrorResponses(app.swagger() as never),
  );
}
