package kfusion.tornado;

import static org.junit.Assert.*;
import kfusion.TornadoModel;
import kfusion.Utils;
import kfusion.algorithms.Raycast;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import tornado.collections.types.Float3;
import tornado.collections.types.FloatOps;
import tornado.collections.types.FloatingPointError;
import tornado.collections.types.ImageFloat3;
import tornado.collections.types.Matrix4x4Float;
import tornado.collections.types.VolumeShort2;
import tornado.meta.domain.DomainTree;
import tornado.meta.domain.IntDomain;
import tornado.runtime.ObjectReference;
import tornado.runtime.api.TaskGraph;
import static tornado.runtime.TornadoRuntime.*;


public class RaycastTesting {

    final private TornadoModel config = new TornadoModel();
	final private String FILE_PATH = "/Users/jamesclarkson/Downloads/kfusion_ut_data";


	final String raycast_prefix = "raycast_";

	private ImageFloat3 vOut;
	private ImageFloat3 nOut;
	private ImageFloat3 pos3D;
	private ImageFloat3 normal;
	private VolumeShort2 volume;
	private Matrix4x4Float view;
	private Float3 volumeDims;
	
	private float nearPlane;
	private float farPlane;
	private float step;
	private float largeStep;

	private TaskGraph graph;
	
	@Before
	public void setUp() throws Exception {

		vOut = new ImageFloat3(320,240);
		nOut = new ImageFloat3(320,240);
		pos3D = new ImageFloat3(320,240);
		normal = new ImageFloat3(320,240);
		volume = new VolumeShort2(256,256,256);
		view = new Matrix4x4Float();
		volumeDims = new Float3(2f,2f,2f);
		
		
		float[] tmp = new float[1];
		Utils.loadData(String.format("%s/%snearPlane.in.%04d",FILE_PATH,raycast_prefix,0),tmp);
		nearPlane = tmp[0];

		Utils.loadData(String.format("%s/%sfarPlane.in.%04d",FILE_PATH,raycast_prefix,0),tmp);
		farPlane = tmp[0];

		Utils.loadData(String.format("%s/%sstep.in.%04d",FILE_PATH,raycast_prefix,0),tmp);
		step = tmp[0];

		Utils.loadData(String.format("%s/%slargestep.in.%04d",FILE_PATH,raycast_prefix,0),tmp);
		largeStep = tmp[0];
		
		DomainTree domain = new DomainTree(2);
		domain.set(0, new IntDomain(vOut.X()));
		domain.set(1, new IntDomain(vOut.Y()));
		
		System.out.printf("      step: %f\n",step);
		System.out.printf("large step: %f\n",largeStep);
//		TornadoExecuteTask raycast = Raycast.raycastCode.invoke(vOut,nOut,volume,volumeDims,view,nearPlane,farPlane,largeStep/0.75f,step);
//		raycast.disableJIT();
//		raycast.meta().addProvider(DomainTree.class, domain);
//		raycast.mapTo(EXTERNAL_GPU);
//		raycast.loadFromFile("opencl/raycast-golden.cl");
//		
		
		graph = new TaskGraph()
			.streamIn(volume,view)
			.add(Raycast::raycast,vOut,nOut,volume,volumeDims,view,nearPlane,farPlane,largeStep/0.75f,step)
			.streamOut(vOut,nOut)
			.mapAllTo(config.getTornadoDevice());
		
		//makeVolatile(volume,view);
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testRaycast() {

		final int[] frames = {0};

		int errors = 0;
		for(int frame=0;frame<frames.length;frame++){
			
			
			
			try{
				Utils.loadData(String.format("%s/%spos3D.out.%04d",FILE_PATH,raycast_prefix,frame),pos3D.asBuffer());
				Utils.loadData(String.format("%s/%snormal.out.%04d",FILE_PATH,raycast_prefix,frame),normal.asBuffer());
				Utils.loadData(String.format("%s/%svolume.in.%04d",FILE_PATH,raycast_prefix,frame),volume.asBuffer());
				Utils.loadData(String.format("%s/%sview.in.%04d",FILE_PATH,raycast_prefix,frame),view.asBuffer());
			} catch(Exception e){
				System.out.println(e.getMessage());
			}
			

			if(config.useTornado()){
                graph.schedule().waitOn();
                graph.dumpTimes();
            } else {
			
			Raycast.raycast(vOut, nOut, volume, volumeDims, view, nearPlane, farPlane, largeStep, step);
			
			}

			System.out.printf("[%d, %d]: vertex=%s, ref=%s\n",11,15,vOut.get(11,15).toString(),pos3D.get(11,15).toString());
			final FloatingPointError vertexError = vOut.calculateULP(pos3D);
			System.out.printf("frame[%d]: vertex errors: %s\n",frame,vertexError.toString());

			final FloatingPointError normalError = nOut.calculateULP(normal);
			System.out.printf("frame[%d]: normal errors: %s\n",frame,normalError.toString());

			if(vertexError.getMaxUlp() > config.getMaxULP() || normalError.getMaxUlp() > config.getMaxULP())
				errors++;
		}

		if(errors > 0){
			fail("Found errors");
		}

	}
	
	private static float calculateULP(ImageFloat3 value, ImageFloat3 ref){
		float maxULP = Float.MIN_VALUE;
		float minULP = Float.MAX_VALUE;
		float averageULP = 0f;

		for(int j=0;j<value.Y();j++){
			for(int i=0;i<value.X();i++){
				final Float3 v = value.get(i, j);
				final Float3 r = ref.get(i, j);
				
				final float ulpFactor = Float3.findULPDistance(v, r);
				averageULP += ulpFactor;
				minULP = Math.min(ulpFactor, minULP);
				maxULP = Math.max(ulpFactor, maxULP);
				
			}
		}
		
		averageULP /= (float) value.X() * value.Y();
		System.out.printf("image: mean ulp=%f, min=%f, max=%f\n",averageULP,minULP,maxULP);

		return maxULP;
	}
	
	 public static void main(String[] args){
		 RaycastTesting testing = new RaycastTesting();

	        try {
	            testing.setUp();

	            testing.testRaycast();
	            testing.tearDown();

	        } catch (Exception e) {
	            // TODO Auto-generated catch block
	            e.printStackTrace();
	        }
	    }

}
