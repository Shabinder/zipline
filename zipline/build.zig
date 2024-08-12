const std = @import("std");
const builtin = @import("builtin");

const targets: []const std.Target.Query = &.{
    .{ .cpu_arch = .aarch64, .os_tag = .macos },
    // .{ .cpu_arch = .aarch64, .os_tag = .windows },
    .{ .cpu_arch = .aarch64, .os_tag = .linux, .abi = .gnu },
    .{ .cpu_arch = .x86_64, .os_tag = .macos },
    // .{ .cpu_arch = .x86_64, .os_tag = .windows },
    .{ .cpu_arch = .x86_64, .os_tag = .linux, .abi = .gnu },
};

pub fn build(b: *std.Build) !void {
    const mode = b.standardOptimizeOption(.{});

    const al = std.heap.page_allocator;

    var version_buf: [128]u8 = undefined;
    const version = try readVersionFile(&version_buf);

    for (targets) |target| {
        const quickjs = b.addSharedLibrary(.{
            .name = "quickjs",
            .version = null,
            .target = b.resolveTargetQuery(target),
            .optimize = mode,
            .pic = true, // Platform-independent code (i.e., relative jumps) to be safe.
        });

        try commonQuickJsSetup(quickjs, version, al);

        const installStep = b.addInstallArtifact(quickjs, .{
            .dest_dir = .{ .override = .{ .custom = try getOutputDir(target, al) } },
        });
        b.getInstallStep().dependOn(&installStep.step);
    }
}

fn commonQuickJsSetup(quickjs: *std.Build.Step.Compile, version: []const u8, al: std.mem.Allocator) !void {
    var quoted_version_buf: [12]u8 = undefined;
    const quoted_version = try std.fmt.bufPrint(&quoted_version_buf, "\"{s}\"", .{version});
    quickjs.defineCMacro("CONFIG_VERSION", quoted_version);

    // Add the JDK's include/ headers.
    const java_home = try std.process.getEnvVarOwned(al, "JAVA_HOME");
    defer al.free(java_home);

    const java_include = try std.fs.path.join(al, &[_][]const u8{ java_home, "include" });
    defer al.free(java_include);

    quickjs.addIncludePath(absPath(java_include));
    // quickjs.addIncludeDir(java_include);

    // Walk the include/ directory for any child dirs (usually platform specific) and add them too.
    const java_include_dir = try std.fs.cwd().openDir(java_include, .{ .iterate = true });
    var jdk_walker = try java_include_dir.walk(al);
    defer jdk_walker.deinit();

    while (try jdk_walker.next()) |entry| {
        switch (entry.kind) {
            .directory => {
                const include_subdir = try std.fs.path.join(al, &[_][]const u8{ java_include, entry.path });
                defer al.free(include_subdir);

                quickjs.addIncludePath(absPath(include_subdir));
                // quickjs.addIncludeDir(include_subdir);
            },
            else => {},
        }
    }

    quickjs.linkLibC();

    quickjs.addCSourceFiles(.{ .files = &.{
        "native/quickjs/cutils.c",
        "native/quickjs/libregexp.c",
        "native/quickjs/libunicode.c",
        "native/quickjs/quickjs.c",
        "native/common/finalization-registry.c",
        "native/common/context-no-eval.c",
    }, .flags = &.{
        "-std=gnu99",
    } });

    quickjs.linkLibCpp();
    quickjs.addCSourceFiles(.{ .files = &.{
        "native/Context.cpp",
        "native/ExceptionThrowers.cpp",
        "native/InboundCallChannel.cpp",
        "native/OutboundCallChannel.cpp",
        "native/quickjs-jni.cpp",
    }, .flags = &.{
        "-std=c++11",
    } });
}

fn readVersionFile(version_buf: []u8) ![]u8 {
    const version_file = try std.fs.cwd().openFile(
        "native/quickjs/VERSION",
        .{ .mode = .read_only },
    );
    defer version_file.close();

    var version_file_reader = std.io.bufferedReader(version_file.reader());
    var version_file_stream = version_file_reader.reader();
    const version = try version_file_stream.readUntilDelimiterOrEof(version_buf, '\n');
    return version.?;
}

fn absPath(path: []const u8) std.Build.LazyPath {
    return std.Build.LazyPath{ .cwd_relative = path };
}

fn getOutputDir(target: std.Target.Query, allocator: std.mem.Allocator) ![]const u8 {
    const os_tag = target.os_tag orelse return error.MissingOSTag;
    const cpu_arch = target.cpu_arch orelse return error.MissingCPUArch;

    const os_dir = switch (os_tag) {
        .macos => "macos",
        .linux => "linux",
        .windows => "windows",
        else => return error.UnsupportedOSTag,
    };

    const arch_dir = switch (cpu_arch) {
        .aarch64 => "arm64",
        .x86_64 => "x64",
        else => return error.UnsupportedCPUArch,
    };

    var buffer: [20]u8 = undefined; // Adjust the size as necessary
    const outputDir = try std.fmt.bufPrint(&buffer, "{s}/{s}", .{ os_dir, arch_dir });

    return allocator.dupe(u8, outputDir);
}
