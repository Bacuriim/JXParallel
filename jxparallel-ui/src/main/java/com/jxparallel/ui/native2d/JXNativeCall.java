package com.jxparallel.ui.native2d;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.SharedLibrary;
import org.lwjgl.system.libffi.FFICIF;
import org.lwjgl.system.libffi.FFIType;
import org.lwjgl.system.libffi.LibFFI;

/**
 * A Windows API function LWJGL does not bind, called through LWJGL's libffi with the platform's
 * convention (stdcall on 32-bit Windows): no extra native library and no AWT. Arguments are passed
 * as Java longs (pointers) or ints, in the declared order; the result comes back as a long. The
 * argument memory is allocated once, so one function is called from one thread at a time.
 */
final class JXNativeCall {
    static final FFIType POINTER = LibFFI.ffi_type_pointer;
    static final FFIType INT = LibFFI.ffi_type_sint32;
    private static final int ABI = org.lwjgl.system.Pointer.POINTER_SIZE == 4 ? LibFFI.FFI_STDCALL : LibFFI.FFI_DEFAULT_ABI;

    private final long address;
    private final FFICIF cif = FFICIF.calloc();
    private final FFIType[] args;
    private final boolean pointerResult;
    /** One 8-byte slot per argument, the pointers to them, and the result. */
    private final long slots;
    private final PointerBuffer argPointers;
    private final long result;

    JXNativeCall(SharedLibrary library, String name, FFIType result, FFIType... args) {
        address = library.getFunctionAddress(name);
        if (address == MemoryUtil.NULL) {
            throw new IllegalStateException(library.getName() + " has no " + name);
        }
        this.args = args;
        pointerResult = result == POINTER;
        PointerBuffer types = MemoryUtil.memAllocPointer(Math.max(1, args.length)); // kept: the cif points to it
        for (FFIType t : args) {
            types.put(t);
        }
        types.flip();
        if (LibFFI.ffi_prep_cif(cif, ABI, result, args.length == 0 ? null : types) != LibFFI.FFI_OK) {
            throw new IllegalStateException("ffi_prep_cif failed for " + name);
        }
        slots = MemoryUtil.nmemCalloc(Math.max(1, args.length), 8);
        argPointers = MemoryUtil.memAllocPointer(Math.max(1, args.length));
        for (int k = 0; k < args.length; k++) {
            argPointers.put(k, slots + 8L * k);
        }
        this.result = MemoryUtil.nmemCalloc(1, 8); // libffi widens integer results to a full register
    }

    synchronized long call(long... values) {
        for (int k = 0; k < args.length; k++) {
            if (args[k] == POINTER) {
                MemoryUtil.memPutAddress(slots + 8L * k, values[k]);
            } else {
                MemoryUtil.memPutInt(slots + 8L * k, (int) values[k]);
            }
        }
        MemoryUtil.memPutLong(result, 0L);
        LibFFI.nffi_call(cif.address(), address, result, argPointers.address());
        return pointerResult ? MemoryUtil.memGetAddress(result) : MemoryUtil.memGetInt(result);
    }
}
