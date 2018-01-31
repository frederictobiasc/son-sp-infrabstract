package sonata.kernel.vimadaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import sonata.kernel.vimadaptor.commons.*;
import sonata.kernel.vimadaptor.commons.nsd.ServiceDescriptor;
import sonata.kernel.vimadaptor.commons.vnfd.VnfDescriptor;
import sonata.kernel.vimadaptor.messaging.ServicePlatformMessage;
import sonata.kernel.vimadaptor.messaging.TestConsumer;
import sonata.kernel.vimadaptor.messaging.TestProducer;

import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class VIMEmuIntegrationTest implements MessageReceiver {
    private Lock accessOutput;
    private Condition outputUnlocked;
    private ObjectMapper mapper;
    private VnfDescriptor vtcVnfd;
    private VnfDescriptor vfwVnfd;
    private ServiceDeployPayload nsdPayload;
    private TestConsumer consumer;
    private String lastHeartbeat = "";
    private String output = null;
    private ServiceDeployPayload data;
    private VnfDescriptor vnfd_socat;
    private VnfDescriptor vnfd_squid;
    private VnfDescriptor vnfd_apache;

    @Before
    /**
     * I'll try to set up an environment, let's see.
     */
    public void setUp() throws IOException, InterruptedException {
        accessOutput = new ReentrantLock();
        outputUnlocked = accessOutput.newCondition();

        System.out.println("Setup Environment");
        ServiceDescriptor serviceDescriptor;
        StringBuilder bodyBuilder = new StringBuilder();
        BufferedReader in = new BufferedReader(new InputStreamReader(
                new FileInputStream(new File("./YAML/sonata-demo.nsd")), Charset.forName("UTF-8")));
        String line;
        while ((line = in.readLine()) != null)
            bodyBuilder.append(line + "\n\r");
        this.mapper = SonataManifestMapper.getSonataMapper();

        serviceDescriptor = mapper.readValue(bodyBuilder.toString(), ServiceDescriptor.class);
        bodyBuilder = new StringBuilder();
        in = new BufferedReader(new InputStreamReader(new FileInputStream(new File("./YAML/vbar.vnfd")),
                Charset.forName("UTF-8")));
        line = null;
        while ((line = in.readLine()) != null)
            bodyBuilder.append(line + "\n\r");
        vtcVnfd = mapper.readValue(bodyBuilder.toString(), VnfDescriptor.class);

        bodyBuilder = new StringBuilder();
        in = new BufferedReader(new InputStreamReader(new FileInputStream(new File("./YAML/vfoo.vnfd")),
                Charset.forName("UTF-8")));
        line = null;
        while ((line = in.readLine()) != null)
            bodyBuilder.append(line + "\n\r");
        vfwVnfd = mapper.readValue(bodyBuilder.toString(), VnfDescriptor.class);


        this.nsdPayload = new ServiceDeployPayload();

        nsdPayload.setServiceDescriptor(serviceDescriptor);
        nsdPayload.addVnfDescriptor(vtcVnfd);
        nsdPayload.addVnfDescriptor(vfwVnfd);
        System.out.println("Test");
        BlockingQueue<ServicePlatformMessage> muxQueue =
                new LinkedBlockingQueue<ServicePlatformMessage>();
        BlockingQueue<ServicePlatformMessage> dispatcherQueue =
                new LinkedBlockingQueue<ServicePlatformMessage>();

        TestProducer producer = new TestProducer(muxQueue, this);
        consumer = new TestConsumer(dispatcherQueue);
        AdaptorCore core = new AdaptorCore(muxQueue, dispatcherQueue, consumer, producer, 0.1);

        core.start();
        int counter = 0;
        // Wait for TestProducer
        while (counter < 2) {
            if (lastHeartbeat.contains("RUNNING")) {
                counter++;
            }
            Thread.sleep(1000);
        }

        bodyBuilder = new StringBuilder();
        in = new BufferedReader(new InputStreamReader(
                new FileInputStream(new File("./YAML/emulator-demo-http-apache-vnfd.yml")), Charset.forName("UTF-8")));
        while ((line = in.readLine()) != null)
            bodyBuilder.append(line + "\n\r");
        this.vnfd_socat = mapper.readValue(bodyBuilder.toString(), VnfDescriptor.class);

        bodyBuilder = new StringBuilder();
        in = new BufferedReader(new InputStreamReader(
                new FileInputStream(new File("./YAML/emulator-demo-l4fw-socat-vnfd.yml")), Charset.forName("UTF-8")));
        while ((line = in.readLine()) != null)
            bodyBuilder.append(line + "\n\r");
        this.vnfd_squid = mapper.readValue(bodyBuilder.toString(), VnfDescriptor.class);

        bodyBuilder = new StringBuilder();
        in = new BufferedReader(new InputStreamReader(
                new FileInputStream(new File("./YAML/emulator-demo-proxy-squid-vnfd.yml")), Charset.forName("UTF-8")));
        while ((line = in.readLine()) != null)
            bodyBuilder.append(line + "\n\r");
        this.vnfd_apache = mapper.readValue(bodyBuilder.toString(), VnfDescriptor.class);


        this.data = new ServiceDeployPayload();
        this.data.setServiceDescriptor(serviceDescriptor);
        this.data.addVnfDescriptor(this.vnfd_apache);
        this.data.addVnfDescriptor(this.vnfd_socat);
        this.data.addVnfDescriptor(this.vnfd_squid);


    }

    @Test
    public void test() throws IOException, InterruptedException {
        // Add first PoP
        System.out.println("[EmulatorTest] Adding PoP 1");
        String addVimBody = "{\"vim_type\":\"VIMEmu\", " + "\"configuration\":{"
                + "\"tenant_ext_router\":\"26f732b2-74bd-4f8c-a60e-dae4fb6a7c14\", "
                + "\"tenant_ext_net\":\"53d43a3e-8c86-48e6-b1cb-f1f2c48833de\"," + "\"tenant\":\"tenantName\","
                + "\"identity_port\":\"32775\""
                + "}," + "\"city\":\"Paderborn\",\"country\":\"Germany\","
                + "\"vim_address\":\"127.0.0.1\",\"username\":\"username\","
                + "\"domain\":\"bla\","
                + "\"name\":\"EmulatorVim1\","
                + "\"pass\":\"password\"}";

        String topic = "infrastructure.management.compute.add";
        ServicePlatformMessage addVimMessage = new ServicePlatformMessage(addVimBody, "application/json", topic,
                UUID.randomUUID().toString(), topic);
        consumer.injectMessage(addVimMessage);
        Thread.sleep(2000);

        waitOnOutput();

        JSONTokener tokener = new JSONTokener(output);
        JSONObject jsonObject = (JSONObject) tokener.nextValue();
        String status = jsonObject.getString("request_status");
        String computeWrapperUuid1 = jsonObject.getString("uuid");
        Assert.assertTrue(status.equals("COMPLETED"));
        System.out.println("OpenStack Wrapper added, with uuid: " + computeWrapperUuid1);

        // List available PoP (Postgres-uuid)
        output = null;
        System.out.println("[EmulatorTest] Listing available NFVIi-PoP.");

        topic = "infrastructure.management.compute.list";
        ServicePlatformMessage listVimMessage =
                new ServicePlatformMessage(null, null, topic, UUID.randomUUID().toString(), topic);
        consumer.injectMessage(listVimMessage);

        waitOnOutput();
        System.out.println("List of PoPs");
        System.out.println(output);
        VimResources[] vimList = mapper.readValue(output, VimResources[].class);
        System.out.println("[TwoPoPTest] Listing available PoP");
        for (VimResources resource : vimList) {
            System.out.println(mapper.writeValueAsString(resource));
        }

        output = null;
        // Prepare the system for a service deployment
        System.out.println("[EmulatorTest] Building service.prepare call.");

        ServicePreparePayload payload = new ServicePreparePayload();

        payload.setInstanceId(data.getNsd().getInstanceUuid());
        ArrayList<VimPreDeploymentList> vims = new ArrayList<VimPreDeploymentList>();
        VimPreDeploymentList vimDepList = new VimPreDeploymentList();
        vimDepList.setUuid(computeWrapperUuid1);
        ArrayList<VnfImage> vnfImages = new ArrayList<VnfImage>();
        VnfImage vnfImgae1 =
                new VnfImage("proxy-squid-img",
                        "file://./images/eu.sonata-nfv_squid-vnf_0.1_1");
        VnfImage vnfImgae2 =
                new VnfImage("l4fw-socat-img",
                        "file://./images/eu.sonata-nfv_socat-vnf_0.1_1");
        VnfImage vnfImgae3 =
                new VnfImage("http-apache-img",
                        "file://./images/eu.sonata-nfv_apache-vnf_0.1_1");

        vnfImages.add(vnfImgae1);
        vnfImages.add(vnfImgae2);
        vnfImages.add(vnfImgae3);

        vimDepList.setImages(vnfImages);
        vims.add(vimDepList);

        payload.setVimList(vims);

        String body = mapper.writeValueAsString(payload);
        System.out.println("[EmulatorTest] Request body:");
        //System.out.println(body);

        topic = "infrastructure.service.prepare";
        ServicePlatformMessage servicePrepareMessage = new ServicePlatformMessage(body,
                "application/x-yaml", topic, UUID.randomUUID().toString(), topic);

        consumer.injectMessage(servicePrepareMessage);

        waitOnOutput();

        tokener = new JSONTokener(output);
        jsonObject = (JSONObject) tokener.nextValue();
        status = null;
        status = jsonObject.getString("request_status");
        String message = jsonObject.getString("message");
        Assert.assertTrue("Failed to prepare the environment for the service deployment: " + status
                + " - message: " + message, status.equals("COMPLETED"));
        System.out.println("Service " + payload.getInstanceId() + " ready for deployment");

        // Deploy each of the VNFs
        ArrayList<VnfRecord> records = new ArrayList<VnfRecord>();

        // deploy apache
        output = null;
        FunctionDeployPayload vnfPayload = new FunctionDeployPayload();
        vnfPayload.setVnfd(this.vnfd_apache);
        vnfPayload.setVimUuid(computeWrapperUuid1);
        vnfPayload.setServiceInstanceId(data.getNsd().getInstanceUuid());
        body = mapper.writeValueAsString(vnfPayload);
        topic = "infrastructure.function.deploy";
        ServicePlatformMessage functionDeployMessage = new ServicePlatformMessage(body,
                "application/x-yaml", topic, UUID.randomUUID().toString(), topic);
        consumer.injectMessage(functionDeployMessage);
        Thread.sleep(2000);
        while (output == null) {
            waitOnOutput();
        }
        Assert.assertNotNull(output);
        int retry = 0;
        int maxRetry = 60;
        while (output.contains("heartbeat") || output.contains("Vim Added") && retry < maxRetry) {
            waitOnOutput();
            retry++;
        }

        System.out.println("FunctionDeployResponse: ");
        System.out.println(output);
        Assert.assertTrue("No response received after function deployment", retry < maxRetry);
        FunctionDeployResponse response = mapper.readValue(output, FunctionDeployResponse.class);
        Assert.assertTrue(response.getRequestStatus().equals("COMPLETED"));
        Assert.assertTrue(response.getVnfr().getStatus() == Status.normal_operation);
        records.add(response.getVnfr());


    }

    private void waitOnOutput() throws InterruptedException {
        while (output == null) {
            accessOutput.lock();
            outputUnlocked.await();
            accessOutput.unlock();
        }
    }


    @Override
    public void receiveHeartbeat(ServicePlatformMessage message) {
        this.lastHeartbeat = message.getBody();
    }

    @Override
    public void receive(ServicePlatformMessage message) {
        accessOutput.lock();
        this.output = message.getBody();
        outputUnlocked.signal();
        accessOutput.unlock();
    }

    @Override
    public void forwardToConsumer(ServicePlatformMessage message) {
        consumer.injectMessage(message);
    }
}
