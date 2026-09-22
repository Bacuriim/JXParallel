# Experimental LWJGL/OpenGL backend

JXParallel now contains an optional `jxparallel-lwjgl` module with a low-level OpenGL backend.
It is not part of the default reactor and does not replace Skija.

## Build

```powershell
mvn -Plwjgl-opengl -pl jxparallel-lwjgl -am test
```

The module uses LWJGL 3.3.3 with GLFW and OpenGL bindings. Platform native artifacts are selected
by Maven profiles for Windows, Linux, and macOS.

## Run the example

```powershell
mvn -Plwjgl-opengl -pl jxparallel-lwjgl -am package -DskipTests
```

The first backend exposes:

- GLFW window creation;
- OpenGL context creation;
- swap interval and render loop;
- scene-graph layout reuse;
- rectangle and outline primitives;
- pointer hit testing and native action dispatch.

## Deliberate limitations

This is an experimental backend, not a complete replacement renderer. It currently does not
provide text rasterization, font fallback, HarfBuzz shaping, image textures, clipping, retained
GPU buffers, accessibility adapters, or Vulkan. Text and advanced controls therefore require
Skija until those subsystems are implemented.

Skija remains the recommended production backend because it already provides text, paths,
compositing, and raster/GPU surface abstractions. LWJGL is intended for experiments where
direct OpenGL control is more important than feature completeness.
