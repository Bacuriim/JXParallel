# Experimental LWJGL graphics backends

JXParallel contains an optional `jxparallel-lwjgl` module with direct LWJGL graphics backends.
The existing `JXComponent`, `JXElement`, `JXNativeNode`, controls, layouts, hit testing and
event dispatch are shared by both backends. Only the window and renderer implementation changes.
The module is not part of the default reactor and does not replace Skija.

## OpenGL build

```powershell
mvn -Plwjgl-opengl -pl jxparallel-lwjgl -am test
```

## Vulkan build

```powershell
mvn -Plwjgl-vulkan -pl jxparallel-lwjgl -am test
```

The Vulkan profile uses LWJGL 3.3.3 Vulkan bindings, GLFW surface creation and shaderc for
runtime GLSL-to-SPIR-V compilation. The platform Vulkan loader and a compatible GPU driver must
be installed separately; `lwjgl-vulkan` itself does not ship a Vulkan driver.
The build is validated in CI-style compilation/tests. On some Windows combinations of Vulkan
loader, overlays and LWJGL 3.3.3, instance capability enumeration can fail before rendering;
that environment-specific failure must be resolved before treating the backend as production
ready.

## Run the examples

```powershell
mvn -Plwjgl-opengl -pl jxparallel-lwjgl -am package -DskipTests
mvn -Plwjgl-vulkan -pl jxparallel-lwjgl -am package -DskipTests
```

The examples are:

- `com.jxparallel.ui.lwjgl.JXLwjglHelloApp`;
- `com.jxparallel.ui.vulkan.JXVulkanHelloApp`.

The direct Vulkan backend provides:

- GLFW window and Vulkan surface;
- physical-device and graphics/present queue selection;
- swapchain, image views, render pass and graphics pipeline;
- runtime shader compilation to SPIR-V;
- host-visible vertex buffer updated from the existing scene graph;
- colored rendering for the existing button, toggle, checkbox, field, progress and slider nodes;
- pointer hit testing and native action dispatch.

The OpenGL backend still exposes:

- GLFW window creation;
- OpenGL context creation;
- swap interval and render loop;
- scene-graph layout reuse;
- rectangle and outline primitives;
- pointer hit testing and native action dispatch.

## Deliberate limitations

These are experimental backends, not complete replacement renderers. The Vulkan path currently
does not provide text rasterization, font fallback, HarfBuzz shaping, image textures, clipping,
retained GPU resource management, swapchain recreation on resize, or accessibility adapters.
Text nodes remain in the shared scene graph but are not rasterized by the direct Vulkan path.

Skija remains the recommended production backend because it already provides text, paths,
compositing, and raster/GPU surface abstractions. Direct LWJGL backends are intended for
experiments where explicit OpenGL/Vulkan control is more important than feature completeness.
