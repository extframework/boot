package net.yakclient.boot.mixin;

import net.yakclient.boot.extension.ExtensionManager;

import java.util.function.Supplier;

public class SourceInjectionWrapperTemplate {
    private final ExtensionManager.ExtKey key;

    private SourceInjectionWrapperTemplate(ExtensionManager.ExtKey key) {
        this.key = key;
    }

    public InjectionExecutionResult run(Supplier<?> injection) {
        try {
            return new InjectionExecutionResult(injection.get(), null);
        } catch (Throwable ex) {
            return new InjectionExecutionResult(null, ex);
        }
    }

    public ExtensionManager.ExtKey getKey() {
        return key;
    }

    public static class InjectionExecutionResult {
        private final Object result;
        private final Throwable exception;

        private InjectionExecutionResult(Object result, Throwable exception) {
            this.result = result;
            this.exception = exception;
        }

        public boolean wasSuccess() {
            return result != null;
        }

        public Object getResult() {
            return result;
        }

        public Throwable getException() {
            return exception;
        }
    }
}
