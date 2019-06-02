/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.linux;

import android.os.Build;
import android.system.OsConstants;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.IOException;

import androidx.annotation.NonNull;
import java8.nio.channels.FileChannel;
import java8.nio.channels.FileChannels;
import me.zhanghai.android.files.provider.linux.syscall.SyscallException;
import me.zhanghai.android.files.provider.linux.syscall.Syscalls;
import me.zhanghai.android.files.reflected.ReflectedAccessor;
import me.zhanghai.android.files.reflected.ReflectedClassMethod;
import me.zhanghai.android.files.reflected.RestrictedHiddenApi;

class LinuxFileChannels {

    private LinuxFileChannels() {}

    @NonNull
    public static FileChannel open(@NonNull FileDescriptor fd, int flags) {
        Closeable closeable = new FileDescriptorCloseable(fd);
        return FileChannels.from(NioUtilsCompat.newFileChannel(closeable, fd, flags));
    }

    private static class FileDescriptorCloseable implements Closeable {

        @NonNull
        private final FileDescriptor mFd;

        public FileDescriptorCloseable(@NonNull FileDescriptor fd) {
            mFd = fd;
        }

        @Override
        public void close() throws IOException {
            try {
                Syscalls.close(mFd);
            } catch (SyscallException e) {
                throw new IOException(e);
            }
        }
    }

    private static class NioUtilsCompat {

        static {
            ReflectedAccessor.allowRestrictedHiddenApiAccess();
        }

        @RestrictedHiddenApi
        private static final ReflectedClassMethod sNewFileChannelMethod = new ReflectedClassMethod(
                "java.nio.NioUtils", "newFileChannel", Closeable.class, FileDescriptor.class,
                int.class);

        @RestrictedHiddenApi
        private static final ReflectedClassMethod sFileChannelImplOpen = new ReflectedClassMethod(
                "sun.nio.ch.FileChannelImpl", "open", FileDescriptor.class, String.class,
                boolean.class, boolean.class, boolean.class, Object.class);

        private NioUtilsCompat() {}

        @NonNull
        public static java.nio.channels.FileChannel newFileChannel(@NonNull Closeable ioObject,
                                                                   @NonNull FileDescriptor fd,
                                                                   int mode) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                return sNewFileChannelMethod.invoke(null, ioObject, fd, mode);
            } else {
                // They broke O_RDONLY by assuming it's non-zero since N, but in fact it is zero.
                // https://android.googlesource.com/platform/libcore/+/nougat-release/luni/src/main/java/java/nio/NioUtils.java#63
                boolean readable = (mode & OsConstants.O_ACCMODE) != OsConstants.O_WRONLY;
                boolean writable = (mode & OsConstants.O_ACCMODE) != OsConstants.O_RDONLY;
                boolean append = (mode & OsConstants.O_APPEND) == OsConstants.O_APPEND;
                return sFileChannelImplOpen.invoke(null, fd, null, readable, writable, append,
                        ioObject);
            }
        }
    }
}
