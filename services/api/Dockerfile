FROM node:26.13.1-trixie-slim@sha256:e6bedef217677b276c591795e169840edd9096dbf13f29df8ef8d1856b8820bc AS build

WORKDIR /workspace
RUN npm install --global --allow-scripts=pnpm pnpm@12.16.1

COPY package.json pnpm-workspace.yaml pnpm-lock.yaml .npmrc ./
COPY services/api/package.json services/api/package.json
RUN pnpm install --frozen-lockfile

COPY . .
RUN pnpm --filter @tandem-for-actual/api build \
    && pnpm --filter @tandem-for-actual/api deploy --prod /production/api

FROM node:26.13.1-trixie-slim@sha256:e6bedef217677b276c591795e169840edd9096dbf13f29df8ef8d1856b8820bc AS runtime

ENV NODE_ENV=production \
    HOST=0.0.0.0 \
    PORT=19753

WORKDIR /app
COPY --from=build --chown=node:node /production/api ./
USER node
EXPOSE 19753
HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=3 \
    CMD node -e "fetch('http://127.0.0.1:19753/extension/v1/health/ready').then((r) => process.exit(r.ok ? 0 : 1)).catch(() => process.exit(1))"
CMD ["node", "dist/entrypoint.js"]
