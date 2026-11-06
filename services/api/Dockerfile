FROM node:26.13.0-trixie-slim@sha256:5adad64f9a1fca2d51972caa65983634c8a774eb300d72b20f7f2a0f60368072 AS build

WORKDIR /workspace
RUN npm install --global --allow-scripts=pnpm pnpm@12.16.1

COPY package.json pnpm-workspace.yaml pnpm-lock.yaml .npmrc ./
COPY services/api/package.json services/api/package.json
RUN pnpm install --frozen-lockfile

COPY . .
RUN pnpm --filter @tandem-for-actual/api build \
    && pnpm --filter @tandem-for-actual/api deploy --prod /production/api

FROM node:26.13.0-trixie-slim@sha256:5adad64f9a1fca2d51972caa65983634c8a774eb300d72b20f7f2a0f60368072 AS runtime

ENV NODE_ENV=production \
    HOST=0.0.0.0 \
    PORT=19753 \
    ACTUAL_API_DATA_DIR=/data/actual-api

WORKDIR /app
COPY --from=build --chown=node:node /production/api ./
RUN mkdir -p /data/actual-api && chown node:node /data/actual-api
USER node
EXPOSE 19753
HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=3 \
    CMD node -e "fetch('http://127.0.0.1:19753/api/v1/health/ready').then((r) => process.exit(r.ok ? 0 : 1)).catch(() => process.exit(1))"
CMD ["node", "dist/entrypoint.js"]
