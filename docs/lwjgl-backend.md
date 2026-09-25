# Experimental raw OpenGL backend

The default native window (`JXWindow` in `jxparallel-ui`) already uses LWJGL: GLFW owns the
window and the OpenGL context, and Skia (Skija) paints the scene graph into the framebuffer.
It needs a 64-bit JVM because Skija ships no 32-bit natives.

The optional `jxparallel-lwjgl` module is a separate experiment that draws the same
`JXNativeNode` tree with raw OpenGL primitives, without Skia. It is not part of the default
reactor.

## Build and run

```powershell
mvn -Plwjgl-opengl -pl jxparallel-lwjgl -am package -DskipTests
```

Example: `com.jxparallel.ui.lwjgl.JXLwjglHelloApp`.

It provides GLFW window and OpenGL context creation, the render loop, scene-graph layout
reuse, rectangle and outline primitives, and pointer hit testing with action dispatch.

## Limitations

No text rasterization, fonts, images, clipping or accessibility adapters. Use `JXWindow`
(Skia on OpenGL) for anything beyond rendering experiments.
