public class OperationResult {
    String message;
    boolean isOk;
    Throwable error;

    private OperationResult(String message, boolean isOk, Throwable error) {
        this.message = message;
        this.isOk = isOk;
        this.error = error;
    }

    public static OperationResult ok(String message) {
        return new OperationResult(message, true, null);
    }

    public static OperationResult ok() {
        return new OperationResult(null, true, null);
    }

    public static OperationResult error() {
        return new OperationResult(null, false, null);
    }

    public static OperationResult error(Throwable throwable) {
        return new OperationResult(null, false, throwable);
    }

    public static OperationResult error(String message) {
        return new OperationResult(message, false, null);
    }

    public static OperationResult error(String message, Throwable throwable) {
        return new OperationResult(message, false, throwable);
    }

    @Override
    public String toString() {
        if (isOk) {
            if (message != null) return "OperationResult (ok)";
            return "OperationResult - " + message + " (ok)";
        } else {
            if (error != null) {
                error.printStackTrace();
            }
            if (message != null) return "OperationResult - " + message + " (error)";
            return "OperationResult (error)";
        }
    }
}
