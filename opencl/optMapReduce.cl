__inline__ void mapValues(float8 tr, __local float * sums){
    __local float * jtj = sums + 7;
    __local float * info = sums + 28;
    
    
    const int result = (int) tr.s7;
    const float error = tr.s6;
    
    if(result < 1) {
        info[1] += result == -4 ? 1 : 0;
        info[2] += result == -5 ? 1 : 0;
        info[3] += result > -4 ? 1 : 0;
        return;
    }
    
    // Error part
    sums[0] += error * error;
    
    // JTe part
    //for(int i = 0; i < 6; ++i)
    //    sums[i+1] += error * tr.si;
    
    float8 sumsp1 = vload8(0,&sums[1]);
    sumsp1 += (float8)(error,error,error,error,error,error,0,0) * (float8) (tr.s01234555);
    vstore8(sumsp1,0,&sums[1]);
    
    const float8 jtj_0_m = (float8) (tr.s00000011);
    const float8 jtj_1_m = (float8) (tr.s11122223);
    const float8 jtj_2_m = (float8) (tr.s3344,tr.s5,0,0,0);
    
    float8 jtj_0 = vload8(0,&jtj[0]);
    float8 jtj_1 = vload8(0,&jtj[8]);
    float8 jtj_2 = vload8(0,&jtj[16]);
    
    //    jtj[0] += tr.s0 * tr.s0;
    //    jtj[1] += tr.s0 * tr.s1;
    //    jtj[2] += tr.s0 * tr.s2;
    //    jtj[3] += tr.s0 * tr.s3;
    //    jtj[4] += tr.s0 * tr.s4;
    //    jtj[5] += tr.s0 * tr.s5;
    //    jtj[6] += tr.s1 * tr.s1;
    //    jtj[7] += tr.s1 * tr.s2;
    
    jtj_0 += jtj_0_m * tr.s01234512;
    vstore8(jtj_0,0,&jtj[0]);
    
    //    jtj[8] += tr.s1 * tr.s3;
    //    jtj[9] += tr.s1 * tr.s4;
    //    jtj[10] += tr.s1 * tr.s5;
    //    jtj[11] += tr.s2 * tr.s2;
    //    jtj[12] += tr.s2 * tr.s3;
    //    jtj[13] += tr.s2 * tr.s4;
    //    jtj[14] += tr.s2 * tr.s5;
    //    jtj[15] += tr.s3 * tr.s3;
    
    jtj_1 += jtj_1_m * tr.s34523453;
    vstore8(jtj_1,0,&jtj[8]);
    
    //    jtj[16] += tr.s3 * tr.s4;
    //    jtj[17] += tr.s3 * tr.s5;
    //    jtj[18] += tr.s4 * tr.s4;
    //    jtj[19] += tr.s4 * tr.s5;
    //    jtj[20] += tr.s5 * tr.s5;
    
    jtj_2 += jtj_2_m * tr.s45455000;
    vstore8(jtj_2,0,&jtj[16]);
    
    // extra info here
    info[0] += 1;
    
    
}

#ifndef WGS
#define WGS 384
#endif

__kernel void optMapReduce(__global uchar *_heap_base, ulong _stack_base, __constant uchar *_constant_region, __local uchar *_local_region, __global uchar *_private_region)
{
    /*
     * obtain arguments from the stack
     */
    
    __global ulong *slots = ((__global ulong *) (((ulong) _heap_base) + _stack_base));
    ulong4 args = vload4(0,&slots[6]);
    
    const int sizeX = (int) args.s2;
    const int sizeY = (int) args.s3;
    
    
    /*
     * field access trackingResult.storage
     */
    ulong a3 = args.y + 32;
    ulong a4 =  *((__global ulong *) a3);
    
    /*
     * define access to raw storage of data
     */
    __global float* trackingResults = (__global float *)  ( a4 + 24);
    __global float* output = (__global float *) (args.x + 24) ;
    
    const int gtid = get_global_id(0);
    const int gdim = get_global_size(0);
    
    const int ltid = get_local_id(0);
    const int ldim =get_local_size(0);
    
    const int wgid = get_group_id(0);
    const int wgdim = get_num_groups(0);
    
    const int index = ltid * 32;
    
    __local float localOutput[WGS * 32];
    __local float *privateOutput = &localOutput[index];
    
    int i;
    
    const int numElements = sizeX * sizeY;
    
    // zero private output
    for(i=0;i<32;i+=4){
        vstore4((float4)(0),0,&privateOutput[i]);
    }
    
    // reduce into private output
    for(int x=gtid;x<numElements;x+=gdim){
        const int index = x * 8;
        float8 result = vload8(0,&trackingResults[index]);
        mapValues(result,privateOutput);
    }
    
    // copy into local output
    barrier(CLK_LOCAL_MEM_FENCE);
    
    const int wgindex = wgid * 32;
    
    if(ltid < 8){
        const int loIndex = ltid * 4;
        float4 sum = (float4)(0);
        for(i=0;i<ldim;i++){
            const int index = i * 32;
            sum += vload4(0,&localOutput[index + loIndex]);
        }
        
        // write to global memory
        vstore4(sum,0,&output[wgindex + loIndex]);
    }
    
    return;
}


__kernel void optMapReduceBump(__global void *bump, __global uchar *_heap_base, ulong _stack_base, __constant uchar *_constant_region, __local uchar *_local_region, __global uchar *_private_region)
{
    /*
     * obtain arguments from the stack
     */
    
    __global ulong *slots = ((__global ulong *) (((ulong) _heap_base) + _stack_base));
    ulong4 args = vload4(0,&slots[6]);
    
    const int sizeX = (int) args.s2;
    const int sizeY = (int) args.s3;
    
    /*
     * field access trackingResult.storage
     */
    ulong a3 = args.y + 32;
    ulong a4 =  *((__global ulong *) a3);
    
    /*
     * define access to raw storage of data
     */
    __global float* trackingResults = (__global float *)  ( a4 + 24);
    __global float* output = (__global float *) (args.x + 24) ;
    
    const int gtid = get_global_id(0);
    const int gdim = get_global_size(0);
    
    const int ltid = get_local_id(0);
    const int ldim =get_local_size(0);
    
    const int wgid = get_group_id(0);
    const int wgdim = get_num_groups(0);
    
    const int index = ltid * 32;
    
    __local float localOutput[WGS * 32];
    __local float *privateOutput = &localOutput[index];
    
    int i;
    
    const int numElements = sizeX * sizeY;
    
    // zero private output
    for(i=0;i<32;i+=4){
        vstore4((float4)(0),0,&privateOutput[i]);
    }
    
    // reduce into private output
    for(int x=gtid;x<numElements;x+=gdim){
        const int index = x * 8;
        float8 result = vload8(0,&trackingResults[index]);
        mapValues(result,privateOutput);
    }
    
    barrier(CLK_LOCAL_MEM_FENCE);
    
    // copy into local output
    const int wgindex = wgid * 32;
    
    if(ltid < 8){
        const int loIndex = ltid * 4;
        float4 sum = (float4)(0);
        for(i=0;i<ldim;i++){
            const int index = i * 32;
            sum += vload4(0,&localOutput[index + loIndex]);
        }
        
        // write to global memory
        vstore4(sum,0,&output[wgindex + loIndex]);
    }
    
    return;
}
