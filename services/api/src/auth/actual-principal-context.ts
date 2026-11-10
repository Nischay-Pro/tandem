import { AsyncLocalStorage } from "node:async_hooks";

const actualPrincipal = new AsyncLocalStorage<string>();

export function getActualPrincipalId(): string | undefined {
  return actualPrincipal.getStore();
}

export function runAsActualPrincipal<T>(
  principalId: string,
  operation: () => T,
): T {
  return actualPrincipal.run(principalId, operation);
}

export function enterActualPrincipal(principalId: string): void {
  actualPrincipal.enterWith(principalId);
}
