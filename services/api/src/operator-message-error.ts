/** An application-controlled diagnostic that is safe to include in logs. */
export class OperatorMessageError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "OperatorMessageError";
  }
}
