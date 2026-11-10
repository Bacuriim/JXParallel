package com.jxparallel.core;

/**
 * Work that receives its {@link CancellationToken} and is expected to check it (cooperative
 * cancellation). See {@link JXParallel#background(CancellableWork)}.
 */
@FunctionalInterface
public interface CancellableWork<T> {
    T call(CancellationToken token) throws Exception;
}
