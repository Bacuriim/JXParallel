package com.jxparallel.ui.vulkan;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.native2d.JXNativeNode;
import com.jxparallel.ui.native2d.JXPointerEvent;

import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.KHRSurface;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkAttachmentDescription;
import org.lwjgl.vulkan.VkAttachmentReference;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo;
import org.lwjgl.vulkan.VkExtent2D;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;
import org.lwjgl.vulkan.VkRenderPassCreateInfo;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.lwjgl.vulkan.VkSubpassDependency;
import org.lwjgl.vulkan.VkSubpassDescription;
import org.lwjgl.vulkan.VkSwapchainCreateInfoKHR;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkQueueFamilyProperties;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;
import org.lwjgl.vulkan.VkViewport;
import org.lwjgl.vulkan.VK;

import static org.lwjgl.glfw.GLFWVulkan.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

public final class JXVulkanWindow implements AutoCloseable {
    private static final int MAX_FRAMES_IN_FLIGHT = 2;
    private static final int VERTEX_STRIDE = 5 * Float.BYTES;
    private static final int MAX_VERTEX_BYTES = 1024 * 1024;

    private final String title;
    private long window;
    private JXNativeNode root;
    private int width = 640;
    private int height = 420;
    private boolean framebufferResized;
    private volatile boolean renderRequested = true;
    private GLFWErrorCallback errorCallback;
    private Runnable onFirstPaint;
    private boolean firstPaintReported;
    private final ConcurrentLinkedQueue<Runnable> pendingActions = new ConcurrentLinkedQueue<Runnable>();

    private VkInstance instance;
    private VkPhysicalDevice physicalDevice;
    private VkDevice device;
    private VkQueue graphicsQueue;
    private VkQueue presentQueue;
    private long surface;
    private long swapchain;
    private VkExtent2D swapchainExtent;
    private int swapchainImageFormat;
    private LongBuffer swapchainImages;
    private LongBuffer swapchainImageViews;
    private long renderPass;
    private long pipelineLayout;
    private long graphicsPipeline;
    private LongBuffer framebuffers;
    private long commandPool;
    private VkCommandBuffer[] commandBuffers;
    private long vertexBuffer;
    private long vertexBufferMemory;
    private ByteBuffer mappedVertexMemory;
    private LongBuffer imageAvailableSemaphores;
    private LongBuffer renderFinishedSemaphores;
    private LongBuffer inFlightFences;
    private int currentFrame;

    public JXVulkanWindow(String title) {
        this.title = title == null ? "JXParallel Vulkan" : title;
    }

    public void setContent(JXElement element) {
        root = JXVulkanRenderer.mount(element);
        requestRender();
    }

    public void setOnFirstPaint(Runnable callback) {
        onFirstPaint = callback;
    }

    public void invokeLater(Runnable action) {
        if (action == null) {
            throw new IllegalArgumentException("Action cannot be null");
        }
        pendingActions.add(action);
        requestRender();
    }

    public void requestRender() {
        renderRequested = true;
        if (window != MemoryUtil.NULL) {
            GLFW.glfwPostEmptyEvent();
        }
    }

    public void show() {
        initWindow();
        try {
            initVulkan();
            loop();
        } finally {
            close();
        }
    }

    private void initWindow() {
        errorCallback = GLFWErrorCallback.createPrint(System.err).set();
        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }
        if (!GLFWVulkan.glfwVulkanSupported()) {
            throw new IllegalStateException("Vulkan is not supported by the active GLFW platform");
        }
        GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);
        window = GLFW.glfwCreateWindow(width, height, title, MemoryUtil.NULL, MemoryUtil.NULL);
        if (window == MemoryUtil.NULL) {
            throw new IllegalStateException("Unable to create GLFW Vulkan window");
        }
        GLFW.glfwSetFramebufferSizeCallback(window, (handle, newWidth, newHeight) -> {
            framebufferResized = true;
            renderRequested = true;
        });
        GLFW.glfwSetMouseButtonCallback(window, (handle, button, action, mods) -> {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && action == GLFW.GLFW_PRESS && root != null) {
                try (MemoryStack stack = stackPush()) {
                    DoubleBufferHolder cursor = new DoubleBufferHolder();
                    GLFW.glfwGetCursorPos(handle, cursor.x, cursor.y);
                    JXNativeNode hit = root.hitTest((int) cursor.x[0], (int) cursor.y[0]);
                    if (hit != null) {
                        hit.dispatchPointer(new JXPointerEvent((int) cursor.x[0], (int) cursor.y[0], button));
                        requestRender();
                    }
                }
            }
        });
    }

    private void initVulkan() {
        createInstance();
        createSurface();
        pickPhysicalDevice();
        createLogicalDevice();
        createSwapchain();
        createImageViews();
        createRenderPass();
        createGraphicsPipeline();
        createFramebuffers();
        createCommandPool();
        createVertexBuffer();
        createCommandBuffers();
        createSyncObjects();
    }

    private void createInstance() {
        try (MemoryStack stack = stackPush()) {
            PointerBuffer requiredExtensions = glfwGetRequiredInstanceExtensions();
            if (requiredExtensions == null) {
                throw new IllegalStateException("GLFW did not provide Vulkan instance extensions");
            }
            PointerBuffer extensions = stack.mallocPointer(requiredExtensions.remaining());
            for (int index = 0; index < requiredExtensions.remaining(); index++) {
                extensions.put(requiredExtensions.get(index));
            }
            extensions.flip();
            VkApplicationInfo appInfo = VkApplicationInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                    .pApplicationName(stack.UTF8("JXParallel"))
                    .applicationVersion(VK_MAKE_VERSION(0, 1, 0))
                    .pEngineName(stack.UTF8("JXParallel"))
                    .engineVersion(VK_MAKE_VERSION(0, 1, 0))
                    .apiVersion(Math.min(VK.getInstanceVersionSupported(), VK_API_VERSION_1_0));
            VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                    .pApplicationInfo(appInfo)
                    .ppEnabledExtensionNames(extensions);
            PointerBuffer pInstance = stack.mallocPointer(1);
            check(vkCreateInstance(createInfo, null, pInstance), "vkCreateInstance");
            instance = new VkInstance(pInstance.get(0), createInfo);
        }
    }

    private void createSurface() {
        try (MemoryStack stack = stackPush()) {
            LongBuffer pSurface = stack.mallocLong(1);
            check(glfwCreateWindowSurface(instance, window, null, pSurface), "glfwCreateWindowSurface");
            surface = pSurface.get(0);
        }
    }

    private void pickPhysicalDevice() {
        try (MemoryStack stack = stackPush()) {
            IntBuffer count = stack.ints(0);
            check(vkEnumeratePhysicalDevices(instance, count, null), "vkEnumeratePhysicalDevices(count)");
            if (count.get(0) == 0) {
                throw new IllegalStateException("No Vulkan physical device found");
            }
            PointerBuffer devices = stack.mallocPointer(count.get(0));
            check(vkEnumeratePhysicalDevices(instance, count, devices), "vkEnumeratePhysicalDevices");
            for (int index = 0; index < devices.capacity(); index++) {
                VkPhysicalDevice candidate = new VkPhysicalDevice(devices.get(index), instance);
                if (findQueueFamily(candidate) >= 0 && supportsSwapchain(candidate)) {
                    physicalDevice = candidate;
                    return;
                }
            }
            throw new IllegalStateException("No Vulkan device supports graphics and presentation");
        }
    }

    private int findQueueFamily(VkPhysicalDevice candidate) {
        try (MemoryStack stack = stackPush()) {
            IntBuffer count = stack.ints(0);
            vkGetPhysicalDeviceQueueFamilyProperties(candidate, count, null);
            VkQueueFamilyProperties.Buffer families =
                    VkQueueFamilyProperties.malloc(count.get(0), stack);
            vkGetPhysicalDeviceQueueFamilyProperties(candidate, count, families);
            for (int index = 0; index < families.capacity(); index++) {
                IntBuffer present = stack.ints(VK_FALSE);
                vkGetPhysicalDeviceSurfaceSupportKHR(candidate, index, surface, present);
                if ((families.get(index).queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0
                        && present.get(0) == VK_TRUE) {
                    return index;
                }
            }
            return -1;
        }
    }

    private boolean supportsSwapchain(VkPhysicalDevice candidate) {
        try (MemoryStack stack = stackPush()) {
            IntBuffer count = stack.ints(0);
            vkGetPhysicalDeviceSurfaceFormatsKHR(candidate, surface, count, null);
            if (count.get(0) == 0) {
                return false;
            }
            vkGetPhysicalDeviceSurfacePresentModesKHR(candidate, surface, count, null);
            return count.get(0) > 0;
        }
    }

    private void createLogicalDevice() {
        int queueFamily = findQueueFamily(physicalDevice);
        try (MemoryStack stack = stackPush()) {
            FloatBuffer priority = stack.floats(1.0f);
            VkDeviceQueueCreateInfo.Buffer queueInfo = VkDeviceQueueCreateInfo.calloc(1, stack);
            queueInfo.get(0)
                    .sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                    .queueFamilyIndex(queueFamily)
                    .pQueuePriorities(priority);
            VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
                    .pQueueCreateInfos(queueInfo)
                    .pEnabledFeatures(VkPhysicalDeviceFeatures.calloc(stack))
                    .ppEnabledExtensionNames(stack.pointers(stack.UTF8(KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME)));
            PointerBuffer pDevice = stack.mallocPointer(1);
            check(vkCreateDevice(physicalDevice, createInfo, null, pDevice), "vkCreateDevice");
            device = new VkDevice(pDevice.get(0), physicalDevice, createInfo);
            PointerBuffer pQueue = stack.mallocPointer(1);
            vkGetDeviceQueue(device, queueFamily, 0, pQueue);
            graphicsQueue = new VkQueue(pQueue.get(0), device);
            presentQueue = graphicsQueue;
        }
    }

    private void createSwapchain() {
        try (MemoryStack stack = stackPush()) {
            VkSurfaceCapabilitiesKHRData capabilities = querySurfaceCapabilities(stack);
            VkSurfaceFormatKHRData format = chooseSurfaceFormat(stack);
            int presentMode = choosePresentMode(stack);
            VkExtent2D extent = chooseExtent(stack, capabilities.capabilities);
            int imageCount = capabilities.capabilities.minImageCount() + 1;
            if (capabilities.capabilities.maxImageCount() > 0) {
                imageCount = Math.min(imageCount, capabilities.capabilities.maxImageCount());
            }
            VkSwapchainCreateInfoKHR createInfo = VkSwapchainCreateInfoKHR.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
                    .surface(surface)
                    .minImageCount(imageCount)
                    .imageFormat(format.format)
                    .imageColorSpace(format.colorSpace)
                    .imageExtent(extent)
                    .imageArrayLayers(1)
                    .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
                    .imageSharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .preTransform(capabilities.capabilities.currentTransform())
                    .compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                    .presentMode(presentMode)
                    .clipped(true);
            LongBuffer pSwapchain = stack.mallocLong(1);
            check(vkCreateSwapchainKHR(device, createInfo, null, pSwapchain), "vkCreateSwapchainKHR");
            swapchain = pSwapchain.get(0);
            swapchainExtent = extent;
            swapchainImageFormat = format.format;
            IntBuffer count = stack.ints(0);
            vkGetSwapchainImagesKHR(device, swapchain, count, null);
            swapchainImages = MemoryUtil.memAllocLong(count.get(0));
            vkGetSwapchainImagesKHR(device, swapchain, count, swapchainImages);
        }
    }

    private VkSurfaceCapabilitiesKHRData querySurfaceCapabilities(MemoryStack stack) {
        org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR capabilities =
                org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR.calloc(stack);
        check(vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice, surface, capabilities),
                "vkGetPhysicalDeviceSurfaceCapabilitiesKHR");
        return new VkSurfaceCapabilitiesKHRData(capabilities);
    }

    private VkSurfaceFormatKHRData chooseSurfaceFormat(MemoryStack stack) {
        IntBuffer count = stack.ints(0);
        vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, count, null);
        org.lwjgl.vulkan.VkSurfaceFormatKHR.Buffer formats =
                org.lwjgl.vulkan.VkSurfaceFormatKHR.malloc(count.get(0), stack);
        vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, count, formats);
        for (int index = 0; index < formats.capacity(); index++) {
            org.lwjgl.vulkan.VkSurfaceFormatKHR format = formats.get(index);
            if (format.format() == VK_FORMAT_B8G8R8A8_SRGB
                    && format.colorSpace() == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                return new VkSurfaceFormatKHRData(format.format(), format.colorSpace());
            }
        }
        return new VkSurfaceFormatKHRData(formats.get(0).format(), formats.get(0).colorSpace());
    }

    private int choosePresentMode(MemoryStack stack) {
        IntBuffer count = stack.ints(0);
        vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, count, null);
        IntBuffer modes = stack.mallocInt(count.get(0));
        vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, count, modes);
        for (int index = 0; index < modes.capacity(); index++) {
            if (modes.get(index) == VK_PRESENT_MODE_MAILBOX_KHR) {
                return VK_PRESENT_MODE_MAILBOX_KHR;
            }
        }
        return VK_PRESENT_MODE_FIFO_KHR;
    }

    private VkExtent2D chooseExtent(MemoryStack stack, org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR capabilities) {
        if (capabilities.currentExtent().width() != 0xFFFFFFFF) {
            return VkExtent2D.calloc(stack).set(capabilities.currentExtent());
        }
        IntBuffer framebufferWidth = stack.mallocInt(1);
        IntBuffer framebufferHeight = stack.mallocInt(1);
        GLFW.glfwGetFramebufferSize(window, framebufferWidth, framebufferHeight);
        return VkExtent2D.calloc(stack)
                .width(Math.max(capabilities.minImageExtent().width(),
                        Math.min(capabilities.maxImageExtent().width(), framebufferWidth.get(0))))
                .height(Math.max(capabilities.minImageExtent().height(),
                        Math.min(capabilities.maxImageExtent().height(), framebufferHeight.get(0))));
    }

    private void createImageViews() {
        swapchainImageViews = MemoryUtil.memAllocLong(swapchainImages.capacity());
        try (MemoryStack stack = stackPush()) {
            VkImageViewCreateInfo createInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                    .viewType(VK_IMAGE_VIEW_TYPE_2D)
                    .format(swapchainImageFormat)
                    .subresourceRange(range(stack));
            for (int index = 0; index < swapchainImages.capacity(); index++) {
                createInfo.image(swapchainImages.get(index));
                LongBuffer view = stack.mallocLong(1);
                check(vkCreateImageView(device, createInfo, null, view), "vkCreateImageView");
                swapchainImageViews.put(index, view.get(0));
            }
        }
    }

    private org.lwjgl.vulkan.VkImageSubresourceRange range(MemoryStack stack) {
        return org.lwjgl.vulkan.VkImageSubresourceRange.calloc(stack)
                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                .baseMipLevel(0)
                .levelCount(1)
                .baseArrayLayer(0)
                .layerCount(1);
    }

    private void createRenderPass() {
        try (MemoryStack stack = stackPush()) {
            VkAttachmentDescription.Buffer colorAttachment = VkAttachmentDescription.calloc(1, stack);
            colorAttachment.get(0)
                    .format(swapchainImageFormat)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                    .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                    .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
            VkAttachmentReference.Buffer colorReference = VkAttachmentReference.calloc(1, stack);
            colorReference.get(0)
                    .attachment(0)
                    .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
            VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack);
            subpass.get(0).pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                    .colorAttachmentCount(1)
                    .pColorAttachments(colorReference);
            VkSubpassDependency.Buffer dependency = VkSubpassDependency.calloc(1, stack);
            dependency.get(0)
                    .srcSubpass(VK_SUBPASS_EXTERNAL)
                    .dstSubpass(0)
                    .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);
            VkRenderPassCreateInfo createInfo = VkRenderPassCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
                    .pAttachments(colorAttachment)
                    .pSubpasses(subpass)
                    .pDependencies(dependency);
            LongBuffer renderPassBuffer = stack.mallocLong(1);
            check(vkCreateRenderPass(device, createInfo, null, renderPassBuffer), "vkCreateRenderPass");
            renderPass = renderPassBuffer.get(0);
        }
    }

    private void createGraphicsPipeline() {
        long vertexShader = createShaderModule(compileShader(VERTEX_SHADER, Shaderc.shaderc_vertex_shader));
        long fragmentShader = createShaderModule(compileShader(FRAGMENT_SHADER, Shaderc.shaderc_fragment_shader));
        try (MemoryStack stack = stackPush()) {
            VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            stages.get(0).sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_VERTEX_BIT).module(vertexShader).pName(stack.UTF8("main"));
            stages.get(1).sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_FRAGMENT_BIT).module(fragmentShader).pName(stack.UTF8("main"));
            VkVertexInputBindingDescription.Buffer binding = VkVertexInputBindingDescription.calloc(1, stack);
            binding.get(0).binding(0).stride(VERTEX_STRIDE).inputRate(VK_VERTEX_INPUT_RATE_VERTEX);
            VkVertexInputAttributeDescription.Buffer attributes =
                    VkVertexInputAttributeDescription.calloc(2, stack);
            attributes.get(0).binding(0).location(0).format(VK_FORMAT_R32G32_SFLOAT).offset(0);
            attributes.get(1).binding(0).location(1).format(VK_FORMAT_R32G32B32_SFLOAT).offset(2 * Float.BYTES);
            VkPipelineVertexInputStateCreateInfo vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
                    .pVertexBindingDescriptions(binding)
                    .pVertexAttributeDescriptions(attributes);
            VkPipelineInputAssemblyStateCreateInfo assembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                    .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
                    .primitiveRestartEnable(false);
            VkViewport.Buffer viewport = VkViewport.calloc(1, stack);
            viewport.get(0).width(swapchainExtent.width()).height(swapchainExtent.height()).maxDepth(1.0f);
            org.lwjgl.vulkan.VkRect2D.Buffer scissors = org.lwjgl.vulkan.VkRect2D.calloc(1, stack);
            scissors.get(0).offset(org.lwjgl.vulkan.VkOffset2D.calloc(stack)).extent(swapchainExtent);
            VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                    .pViewports(viewport).pScissors(scissors);
            VkPipelineRasterizationStateCreateInfo rasterizer = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                    .depthClampEnable(false).rasterizerDiscardEnable(false)
                    .polygonMode(VK_POLYGON_MODE_FILL).lineWidth(1.0f)
                    .cullMode(VK_CULL_MODE_NONE).frontFace(VK_FRONT_FACE_CLOCKWISE);
            VkPipelineMultisampleStateCreateInfo multisampling = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                    .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);
            VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachment =
                    VkPipelineColorBlendAttachmentState.calloc(1, stack);
            colorBlendAttachment.get(0).colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT
                    | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT);
            VkPipelineColorBlendStateCreateInfo colorBlending = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
                    .logicOpEnable(false).pAttachments(colorBlendAttachment);
            VkPipelineLayoutCreateInfo layoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO);
            LongBuffer pLayout = stack.mallocLong(1);
            check(vkCreatePipelineLayout(device, layoutInfo, null, pLayout), "vkCreatePipelineLayout");
            pipelineLayout = pLayout.get(0);
            VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                    .pStages(stages).pVertexInputState(vertexInput).pInputAssemblyState(assembly)
                    .pViewportState(viewportState).pRasterizationState(rasterizer)
                    .pMultisampleState(multisampling).pColorBlendState(colorBlending)
                    .layout(pipelineLayout).renderPass(renderPass).subpass(0);
            LongBuffer pPipeline = stack.mallocLong(1);
            check(vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, pipelineInfo, null, pPipeline),
                    "vkCreateGraphicsPipelines");
            graphicsPipeline = pPipeline.get(0);
        } finally {
            vkDestroyShaderModule(device, vertexShader, null);
            vkDestroyShaderModule(device, fragmentShader, null);
        }
    }

    private ByteBuffer compileShader(String source, int kind) {
        long compiler = Shaderc.shaderc_compiler_initialize();
        if (compiler == 0) {
            throw new IllegalStateException("Unable to initialize shaderc");
        }
        long result = 0;
        try {
            result = Shaderc.shaderc_compile_into_spv(compiler, source, kind, "jxparallel.glsl", "main", 0);
            if (result == 0 || Shaderc.shaderc_result_get_compilation_status(result)
                    != Shaderc.shaderc_compilation_status_success) {
                String error = result == 0 ? "unknown shaderc error"
                        : Shaderc.shaderc_result_get_error_message(result);
                throw new IllegalStateException("Vulkan shader compilation failed: " + error);
            }
            return Shaderc.shaderc_result_get_bytes(result);
        } finally {
            if (result != 0) {
                Shaderc.shaderc_result_release(result);
            }
            Shaderc.shaderc_compiler_release(compiler);
        }
    }

    private long createShaderModule(ByteBuffer code) {
        try (MemoryStack stack = stackPush()) {
            VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
                    .pCode(code);
            LongBuffer shaderModule = stack.mallocLong(1);
            check(vkCreateShaderModule(device, createInfo, null, shaderModule), "vkCreateShaderModule");
            return shaderModule.get(0);
        }
    }

    private void createFramebuffers() {
        framebuffers = MemoryUtil.memAllocLong(swapchainImageViews.capacity());
        try (MemoryStack stack = stackPush()) {
            VkFramebufferCreateInfo createInfo = VkFramebufferCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
                    .renderPass(renderPass).width(swapchainExtent.width()).height(swapchainExtent.height())
                    .layers(1);
            LongBuffer attachments = stack.mallocLong(1);
            for (int index = 0; index < swapchainImageViews.capacity(); index++) {
                attachments.put(0, swapchainImageViews.get(index));
                createInfo.pAttachments(attachments);
                LongBuffer framebuffer = stack.mallocLong(1);
                check(vkCreateFramebuffer(device, createInfo, null, framebuffer), "vkCreateFramebuffer");
                framebuffers.put(index, framebuffer.get(0));
            }
        }
    }

    private void createCommandPool() {
        try (MemoryStack stack = stackPush()) {
            VkCommandPoolCreateInfo createInfo = VkCommandPoolCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
                    .queueFamilyIndex(findQueueFamily(physicalDevice));
            LongBuffer pool = stack.mallocLong(1);
            check(vkCreateCommandPool(device, createInfo, null, pool), "vkCreateCommandPool");
            commandPool = pool.get(0);
        }
    }

    private void createVertexBuffer() {
        try (MemoryStack stack = stackPush()) {
            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                    .size(MAX_VERTEX_BYTES)
                    .usage(VK_BUFFER_USAGE_VERTEX_BUFFER_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            LongBuffer buffer = stack.mallocLong(1);
            check(vkCreateBuffer(device, bufferInfo, null, buffer), "vkCreateBuffer");
            vertexBuffer = buffer.get(0);
            VkMemoryRequirements requirements = VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(device, vertexBuffer, requirements);
            VkMemoryAllocateInfo allocation = VkMemoryAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                    .allocationSize(requirements.size())
                    .memoryTypeIndex(findMemoryType(requirements.memoryTypeBits(),
                            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT));
            LongBuffer memory = stack.mallocLong(1);
            check(vkAllocateMemory(device, allocation, null, memory), "vkAllocateMemory");
            vertexBufferMemory = memory.get(0);
            check(vkBindBufferMemory(device, vertexBuffer, vertexBufferMemory, 0), "vkBindBufferMemory");
            PointerBuffer mapped = stack.mallocPointer(1);
            check(vkMapMemory(device, vertexBufferMemory, 0, MAX_VERTEX_BYTES, 0, mapped), "vkMapMemory");
            mappedVertexMemory = mapped.getByteBuffer(0, MAX_VERTEX_BYTES);
        }
    }

    private int findMemoryType(int typeFilter, int properties) {
        VkPhysicalDeviceMemoryProperties memoryProperties =
                VkPhysicalDeviceMemoryProperties.malloc();
        try {
            vkGetPhysicalDeviceMemoryProperties(physicalDevice, memoryProperties);
            for (int index = 0; index < memoryProperties.memoryTypeCount(); index++) {
                if ((typeFilter & (1 << index)) != 0
                        && (memoryProperties.memoryTypes(index).propertyFlags() & properties) == properties) {
                    return index;
                }
            }
            throw new IllegalStateException("Unable to find suitable Vulkan memory type");
        } finally {
            memoryProperties.free();
        }
    }

    private void createCommandBuffers() {
        try (MemoryStack stack = stackPush()) {
            VkCommandBufferAllocateInfo allocation = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                    .commandPool(commandPool).level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(framebuffers.capacity());
            PointerBuffer handles = stack.mallocPointer(framebuffers.capacity());
            check(vkAllocateCommandBuffers(device, allocation, handles), "vkAllocateCommandBuffers");
            commandBuffers = new VkCommandBuffer[framebuffers.capacity()];
            for (int index = 0; index < commandBuffers.length; index++) {
                commandBuffers[index] = new VkCommandBuffer(handles.get(index), device);
            }
        }
    }

    private void createSyncObjects() {
        imageAvailableSemaphores = MemoryUtil.memAllocLong(MAX_FRAMES_IN_FLIGHT);
        renderFinishedSemaphores = MemoryUtil.memAllocLong(MAX_FRAMES_IN_FLIGHT);
        inFlightFences = MemoryUtil.memAllocLong(MAX_FRAMES_IN_FLIGHT);
        try (MemoryStack stack = stackPush()) {
            VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);
            VkFenceCreateInfoData fenceInfo = new VkFenceCreateInfoData(stack);
            for (int index = 0; index < MAX_FRAMES_IN_FLIGHT; index++) {
                LongBuffer semaphore = stack.mallocLong(1);
                check(vkCreateSemaphore(device, semaphoreInfo, null, semaphore), "vkCreateSemaphore");
                imageAvailableSemaphores.put(index, semaphore.get(0));
                check(vkCreateSemaphore(device, semaphoreInfo, null, semaphore), "vkCreateSemaphore");
                renderFinishedSemaphores.put(index, semaphore.get(0));
                check(vkCreateFence(device, fenceInfo.info, null, semaphore), "vkCreateFence");
                inFlightFences.put(index, semaphore.get(0));
            }
        }
    }

    private void loop() {
        while (!GLFW.glfwWindowShouldClose(window)) {
            boolean hadPending = false;
            Runnable action;
            while ((action = pendingActions.poll()) != null) {
                action.run();
                hadPending = true;
            }
            if (hadPending) {
                renderRequested = true;
            }
            if (renderRequested || framebufferResized || !firstPaintReported) {
                renderRequested = false;
                drawFrame();
                GLFW.glfwPollEvents();
            } else {
                GLFW.glfwWaitEventsTimeout(0.016);
            }
        }
        vkDeviceWaitIdle(device);
    }

    private void drawFrame() {
        try (MemoryStack stack = stackPush()) {
            long fence = inFlightFences.get(currentFrame);
            check(vkWaitForFences(device, fence, true, Long.MAX_VALUE), "vkWaitForFences");
            IntBuffer imageIndex = stack.mallocInt(1);
            int acquire = vkAcquireNextImageKHR(device, swapchain, Long.MAX_VALUE,
                    imageAvailableSemaphores.get(currentFrame), VK_NULL_HANDLE, imageIndex);
            if (acquire == VK_ERROR_OUT_OF_DATE_KHR) {
                framebufferResized = false;
                return;
            }
            check(acquire, "vkAcquireNextImageKHR");
            int vertexCount = updateVertexBuffer();
            recordCommandBuffer(commandBuffers[imageIndex.get(0)], imageIndex.get(0), vertexCount);
            check(vkResetFences(device, fence), "vkResetFences");
            VkSubmitInfo submit = VkSubmitInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                    .pWaitSemaphores(stack.longs(imageAvailableSemaphores.get(currentFrame)))
                    .pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT))
                    .pCommandBuffers(stack.pointers(commandBuffers[imageIndex.get(0)]))
                    .pSignalSemaphores(stack.longs(renderFinishedSemaphores.get(currentFrame)));
            check(vkQueueSubmit(graphicsQueue, submit, fence), "vkQueueSubmit");
            VkPresentInfoKHR present = VkPresentInfoKHR.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
                    .pWaitSemaphores(stack.longs(renderFinishedSemaphores.get(currentFrame)))
                    .swapchainCount(1).pSwapchains(stack.longs(swapchain))
                    .pImageIndices(imageIndex);
            int presentResult = vkQueuePresentKHR(presentQueue, present);
            if (presentResult != VK_SUCCESS && presentResult != VK_SUBOPTIMAL_KHR) {
                check(presentResult, "vkQueuePresentKHR");
            }
            if (!firstPaintReported) {
                firstPaintReported = true;
                if (onFirstPaint != null) {
                    onFirstPaint.run();
                }
            }
            currentFrame = (currentFrame + 1) % MAX_FRAMES_IN_FLIGHT;
        }
    }

    private int updateVertexBuffer() {
        mappedVertexMemory.clear();
        FloatBuffer vertices = mappedVertexMemory.asFloatBuffer();
        int vertexCount = JXVulkanRenderer.writeVertices(root, swapchainExtent.width(), swapchainExtent.height(), vertices);
        vertices.flip();
        return vertexCount;
    }

    private void recordCommandBuffer(VkCommandBuffer commandBuffer, int imageIndex, int vertexCount) {
        try (MemoryStack stack = stackPush()) {
            check(vkResetCommandBuffer(commandBuffer, 0), "vkResetCommandBuffer");
            VkCommandBufferBeginInfo begin = VkCommandBufferBeginInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
            check(vkBeginCommandBuffer(commandBuffer, begin), "vkBeginCommandBuffer");
            VkClearValue.Buffer clear = VkClearValue.calloc(1, stack);
            clear.get(0).color().float32(0, 0.97f).float32(1, 0.97f)
                    .float32(2, 0.97f).float32(3, 1.0f);
            VkRenderPassBeginInfo renderPassBegin = VkRenderPassBeginInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                    .renderPass(renderPass).framebuffer(framebuffers.get(imageIndex))
                    .renderArea(new VkRect2DData(stack, swapchainExtent).rect)
                    .pClearValues(clear);
            vkCmdBeginRenderPass(commandBuffer, renderPassBegin, VK_SUBPASS_CONTENTS_INLINE);
            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, graphicsPipeline);
            vkCmdBindVertexBuffers(commandBuffer, 0, stack.longs(vertexBuffer), stack.longs(0));
            vkCmdDraw(commandBuffer, vertexCount, 1, 0, 0);
            vkCmdEndRenderPass(commandBuffer);
            check(vkEndCommandBuffer(commandBuffer), "vkEndCommandBuffer");
        }
    }

    @Override
    public void close() {
        if (device != null) {
            vkDeviceWaitIdle(device);
            if (mappedVertexMemory != null) {
                vkUnmapMemory(device, vertexBufferMemory);
            }
            if (inFlightFences != null) {
                for (int index = 0; index < inFlightFences.capacity(); index++) {
                    vkDestroyFence(device, inFlightFences.get(index), null);
                }
                MemoryUtil.memFree(inFlightFences);
            }
            if (imageAvailableSemaphores != null) {
                for (int index = 0; index < imageAvailableSemaphores.capacity(); index++) {
                    vkDestroySemaphore(device, imageAvailableSemaphores.get(index), null);
                    vkDestroySemaphore(device, renderFinishedSemaphores.get(index), null);
                }
                MemoryUtil.memFree(imageAvailableSemaphores);
                MemoryUtil.memFree(renderFinishedSemaphores);
            }
            if (vertexBuffer != 0) {
                vkDestroyBuffer(device, vertexBuffer, null);
                vkFreeMemory(device, vertexBufferMemory, null);
            }
            if (commandPool != 0) {
                vkDestroyCommandPool(device, commandPool, null);
            }
            if (framebuffers != null) {
                for (int index = 0; index < framebuffers.capacity(); index++) {
                    vkDestroyFramebuffer(device, framebuffers.get(index), null);
                }
                MemoryUtil.memFree(framebuffers);
            }
            if (graphicsPipeline != 0) {
                vkDestroyPipeline(device, graphicsPipeline, null);
            }
            if (pipelineLayout != 0) {
                vkDestroyPipelineLayout(device, pipelineLayout, null);
            }
            if (renderPass != 0) {
                vkDestroyRenderPass(device, renderPass, null);
            }
            if (swapchainImageViews != null) {
                for (int index = 0; index < swapchainImageViews.capacity(); index++) {
                    vkDestroyImageView(device, swapchainImageViews.get(index), null);
                }
                MemoryUtil.memFree(swapchainImageViews);
            }
            if (swapchainImages != null) {
                MemoryUtil.memFree(swapchainImages);
            }
            if (swapchain != 0) {
                vkDestroySwapchainKHR(device, swapchain, null);
            }
            vkDestroyDevice(device, null);
        }
        if (surface != 0) {
            vkDestroySurfaceKHR(instance, surface, null);
        }
        if (instance != null) {
            vkDestroyInstance(instance, null);
        }
        VK.destroy();
        if (window != MemoryUtil.NULL) {
            GLFW.glfwDestroyWindow(window);
            window = MemoryUtil.NULL;
        }
        if (errorCallback != null) {
            errorCallback.free();
            errorCallback = null;
        }
        if (GLFW.glfwGetPlatform() != GLFW.GLFW_PLATFORM_NULL) {
            GLFW.glfwTerminate();
        }
    }

    private static void check(int result, String operation) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(operation + " failed with VkResult " + result);
        }
    }

    private static final String VERTEX_SHADER =
            "#version 450\n"
                    + "layout(location = 0) in vec2 inPosition;\n"
                    + "layout(location = 1) in vec3 inColor;\n"
                    + "layout(location = 0) out vec3 color;\n"
                    + "void main() { gl_Position = vec4(inPosition, 0.0, 1.0); color = inColor; }\n";

    private static final String FRAGMENT_SHADER =
            "#version 450\n"
                    + "layout(location = 0) in vec3 color;\n"
                    + "layout(location = 0) out vec4 outColor;\n"
                    + "void main() { outColor = vec4(color, 1.0); }\n";

    private static final class VkSurfaceCapabilitiesKHRData {
        private final org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR capabilities;

        private VkSurfaceCapabilitiesKHRData(org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR capabilities) {
            this.capabilities = capabilities;
        }
    }

    private static final class VkSurfaceFormatKHRData {
        private final int format;
        private final int colorSpace;

        private VkSurfaceFormatKHRData(int format, int colorSpace) {
            this.format = format;
            this.colorSpace = colorSpace;
        }
    }

    private static final class VkRect2DData {
        private final org.lwjgl.vulkan.VkRect2D rect;

        private VkRect2DData(MemoryStack stack, VkExtent2D extent) {
            rect = org.lwjgl.vulkan.VkRect2D.calloc(stack)
                    .offset(org.lwjgl.vulkan.VkOffset2D.calloc(stack))
                    .extent(extent);
        }
    }

    private static final class VkFenceCreateInfoData {
        private final org.lwjgl.vulkan.VkFenceCreateInfo info;

        private VkFenceCreateInfoData(MemoryStack stack) {
            info = org.lwjgl.vulkan.VkFenceCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
                    .flags(VK_FENCE_CREATE_SIGNALED_BIT);
        }
    }

    private static final class DoubleBufferHolder {
        private final double[] x = new double[1];
        private final double[] y = new double[1];
    }
}
