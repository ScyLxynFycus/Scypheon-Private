#define CL_NO_PROTOTYPES
#include <CL/cl.h>
#include <dlfcn.h>
#include <stdio.h>
#include <mutex>

// Define function pointers for all used OpenCL APIs
extern "C" {
    cl_int (CL_API_CALL *clGetPlatformIDs)(cl_uint, cl_platform_id *, cl_uint *) = nullptr;
    cl_int (CL_API_CALL *clGetPlatformInfo)(cl_platform_id, cl_platform_info, size_t, void *, size_t *) = nullptr;
    cl_int (CL_API_CALL *clGetDeviceIDs)(cl_platform_id, cl_device_type, cl_uint, cl_device_id *, cl_uint *) = nullptr;
    cl_int (CL_API_CALL *clGetDeviceInfo)(cl_device_id, cl_device_info, size_t, void *, size_t *) = nullptr;
    cl_context (CL_API_CALL *clCreateContext)(const cl_context_properties *, cl_uint, const cl_device_id *, void (CL_CALLBACK *)(const char *, const void *, size_t, void *), void *, cl_int *) = nullptr;
    cl_command_queue (CL_API_CALL *clCreateCommandQueue)(cl_context, cl_device_id, cl_command_queue_properties, cl_int *) = nullptr;
    cl_command_queue (CL_API_CALL *clCreateCommandQueueWithProperties)(cl_context, cl_device_id, const cl_queue_properties *, cl_int *) = nullptr;
    cl_mem (CL_API_CALL *clCreateBuffer)(cl_context, cl_mem_flags, size_t, void *, cl_int *) = nullptr;
    cl_mem (CL_API_CALL *clCreateBufferWithProperties)(cl_context, const cl_mem_properties *, cl_mem_flags, size_t, void *, cl_int *) = nullptr;
    cl_mem (CL_API_CALL *clCreateSubBuffer)(cl_mem, cl_mem_flags, cl_buffer_create_type, const void *, cl_int *) = nullptr;
    cl_mem (CL_API_CALL *clCreateImage)(cl_context, cl_mem_flags, const cl_image_format *, const cl_image_desc *, void *, cl_int *) = nullptr;
    cl_int (CL_API_CALL *clReleaseMemObject)(cl_mem) = nullptr;
    cl_int (CL_API_CALL *clGetMemObjectInfo)(cl_mem, cl_mem_info, size_t, void *, size_t *) = nullptr;
    cl_program (CL_API_CALL *clCreateProgramWithSource)(cl_context, cl_uint, const char **, const size_t *, cl_int *) = nullptr;
    cl_int (CL_API_CALL *clReleaseProgram)(cl_program) = nullptr;
    cl_int (CL_API_CALL *clBuildProgram)(cl_program, cl_uint, const cl_device_id *, const char *, void (CL_CALLBACK *)(cl_program, void *), void *) = nullptr;
    cl_int (CL_API_CALL *clGetProgramBuildInfo)(cl_program, cl_device_id, cl_program_build_info, size_t, void *, size_t *) = nullptr;
    cl_kernel (CL_API_CALL *clCreateKernel)(cl_program, const char *, cl_int *) = nullptr;
    cl_int (CL_API_CALL *clReleaseKernel)(cl_kernel) = nullptr;
    cl_int (CL_API_CALL *clSetKernelArg)(cl_kernel, cl_uint, size_t, const void *) = nullptr;
    cl_int (CL_API_CALL *clGetKernelInfo)(cl_kernel, cl_kernel_info, size_t, void *, size_t *) = nullptr;
    cl_int (CL_API_CALL *clGetKernelWorkGroupInfo)(cl_kernel, cl_device_id, cl_kernel_work_group_info, size_t, void *, size_t *) = nullptr;
    cl_int (CL_API_CALL *clGetKernelSubGroupInfo)(cl_kernel, cl_device_id, cl_kernel_sub_group_info, size_t, const void *, size_t, void *, size_t *) = nullptr;
    cl_int (CL_API_CALL *clEnqueueNDRangeKernel)(cl_command_queue, cl_kernel, cl_uint, const size_t *, const size_t *, const size_t *, cl_uint, const cl_event *, cl_event *) = nullptr;
    cl_int (CL_API_CALL *clEnqueueReadBuffer)(cl_command_queue, cl_mem, cl_bool, size_t, size_t, void *, cl_uint, const cl_event *, cl_event *) = nullptr;
    cl_int (CL_API_CALL *clEnqueueWriteBuffer)(cl_command_queue, cl_mem, cl_bool, size_t, size_t, const void *, cl_uint, const cl_event *, cl_event *) = nullptr;
    cl_int (CL_API_CALL *clEnqueueCopyBuffer)(cl_command_queue, cl_mem, cl_mem, size_t, size_t, size_t, cl_uint, const cl_event *, cl_event *) = nullptr;
    cl_int (CL_API_CALL *clEnqueueFillBuffer)(cl_command_queue, cl_mem, const void *, size_t, size_t, size_t, cl_uint, const cl_event *, cl_event *) = nullptr;
    void * (CL_API_CALL *clEnqueueMapBuffer)(cl_command_queue, cl_mem, cl_bool, cl_map_flags, size_t, size_t, cl_uint, const cl_event *, cl_event *, cl_int *) = nullptr;
    cl_int (CL_API_CALL *clEnqueueUnmapMemObject)(cl_command_queue, cl_mem, void *, cl_uint, const cl_event *, cl_event *) = nullptr;
    cl_int (CL_API_CALL *clEnqueueBarrierWithWaitList)(cl_command_queue, cl_uint, const cl_event *, cl_event *) = nullptr;
    cl_int (CL_API_CALL *clEnqueueMarkerWithWaitList)(cl_command_queue, cl_uint, const cl_event *, cl_event *) = nullptr;
    cl_int (CL_API_CALL *clFinish)(cl_command_queue) = nullptr;
    cl_int (CL_API_CALL *clFlush)(cl_command_queue) = nullptr;
    cl_int (CL_API_CALL *clReleaseContext)(cl_context) = nullptr;
    cl_int (CL_API_CALL *clReleaseCommandQueue)(cl_command_queue) = nullptr;
    cl_int (CL_API_CALL *clGetCommandQueueInfo)(cl_command_queue, cl_command_queue_info, size_t, void *, size_t *) = nullptr;
    cl_int (CL_API_CALL *clGetEventProfilingInfo)(cl_event, cl_profiling_info, size_t, void *, size_t *) = nullptr;
    cl_int (CL_API_CALL *clGetEventInfo)(cl_event, cl_event_info, size_t, void *, size_t *) = nullptr;
    cl_int (CL_API_CALL *clReleaseEvent)(cl_event) = nullptr;
    cl_int (CL_API_CALL *clWaitForEvents)(cl_uint, const cl_event *) = nullptr;
    cl_int (CL_API_CALL *clGetContextInfo)(cl_context, cl_context_info, size_t, void *, size_t *) = nullptr;
}

static void* g_opencl_handle = nullptr;
static std::once_flag g_opencl_init_flag;

bool ggml_opencl_load_dynamic() {
    std::call_once(g_opencl_init_flag, []() {
        const char* lib_names[] = {
            "libOpenCL.so",
            "/system/lib64/libOpenCL.so",
            "/system/vendor/lib64/libOpenCL.so",
            "/vendor/lib64/libOpenCL.so",
            "/system/vendor/lib64/egl/libGLES_mali.so", // Common on Mali devices
            "/system/lib/libOpenCL.so",
            "/system/vendor/lib/libOpenCL.so"
        };

        for (const char* name : lib_names) {
            g_opencl_handle = dlopen(name, RTLD_NOW | RTLD_GLOBAL);
            if (g_opencl_handle) {
                // printf("ggml_opencl: Loaded %s\n", name);
                break;
            }
        }

        if (!g_opencl_handle) return;

        #define LOAD_SYMBOL(name) name = (decltype(name)) dlsym(g_opencl_handle, #name)

        LOAD_SYMBOL(clGetPlatformIDs);
        LOAD_SYMBOL(clGetPlatformInfo);
        LOAD_SYMBOL(clGetDeviceIDs);
        LOAD_SYMBOL(clGetDeviceInfo);
        LOAD_SYMBOL(clCreateContext);
        LOAD_SYMBOL(clCreateCommandQueue);
        LOAD_SYMBOL(clCreateCommandQueueWithProperties);
        LOAD_SYMBOL(clCreateBuffer);
        LOAD_SYMBOL(clCreateBufferWithProperties);
        LOAD_SYMBOL(clCreateSubBuffer);
        LOAD_SYMBOL(clCreateImage);
        LOAD_SYMBOL(clReleaseMemObject);
        LOAD_SYMBOL(clGetMemObjectInfo);
        LOAD_SYMBOL(clCreateProgramWithSource);
        LOAD_SYMBOL(clReleaseProgram);
        LOAD_SYMBOL(clBuildProgram);
        LOAD_SYMBOL(clGetProgramBuildInfo);
        LOAD_SYMBOL(clCreateKernel);
        LOAD_SYMBOL(clReleaseKernel);
        LOAD_SYMBOL(clSetKernelArg);
        LOAD_SYMBOL(clGetKernelInfo);
        LOAD_SYMBOL(clGetKernelWorkGroupInfo);
        LOAD_SYMBOL(clGetKernelSubGroupInfo);
        LOAD_SYMBOL(clEnqueueNDRangeKernel);
        LOAD_SYMBOL(clEnqueueReadBuffer);
        LOAD_SYMBOL(clEnqueueWriteBuffer);
        LOAD_SYMBOL(clEnqueueCopyBuffer);
        LOAD_SYMBOL(clEnqueueFillBuffer);
        LOAD_SYMBOL(clEnqueueMapBuffer);
        LOAD_SYMBOL(clEnqueueUnmapMemObject);
        LOAD_SYMBOL(clEnqueueBarrierWithWaitList);
        LOAD_SYMBOL(clEnqueueMarkerWithWaitList);
        LOAD_SYMBOL(clFinish);
        LOAD_SYMBOL(clFlush);
        LOAD_SYMBOL(clReleaseContext);
        LOAD_SYMBOL(clReleaseCommandQueue);
        LOAD_SYMBOL(clGetCommandQueueInfo);
        LOAD_SYMBOL(clGetEventProfilingInfo);
        LOAD_SYMBOL(clGetEventInfo);
        LOAD_SYMBOL(clReleaseEvent);
        LOAD_SYMBOL(clWaitForEvents);
        LOAD_SYMBOL(clGetContextInfo);
    });

    return g_opencl_handle != nullptr && clGetPlatformIDs != nullptr;
}
