package sonata.kernel.vimadaptor;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.lang.reflect.Array;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


/**
 * This is (technically) not a unit test
 * Information: https://github.com/sonata-nfv/son-sp-infrabstract/wiki/Vim-Emulator-Wrapper-Development-and-Test-Setup
 * @TODO test setup: https://github.com/sonata-nfv/son-examples/tree/master/service-projects/sonata-fw-vtc-service-emu
 * known as "Y1 demo service" consisting of
 * - firewall
 * - vtc (video telephone conferencing?)
 */
public class VIMEmuIntegrationTest implements MessageReceiver {
    private Lock accessOutput;
    private Condition outputUnlocked;
    private ObjectMapper mapper;
    private VnfDescriptor vtcVnfd;
    private VnfDescriptor vfwVnfd;
    private ServiceDescriptor networkServiceDescriptor;
    private ServiceDeployPayload nsdPayload;
    private TestConsumer consumer;
    private String lastHeartbeat = "";
    private String output = null;
    private ArrayList<VnfRecord> vnfRecords;

    @Before
    /**
     * I'll try to set up an environment, let's see.
     */
    public void setUp() throws IOException, InterruptedException {
        // Start AdapterCore and initialize communication for MessageReceiver
        System.out.println("Setup Environment");
        accessOutput = new ReentrantLock();
        outputUnlocked = accessOutput.newCondition();
        BlockingQueue<ServicePlatformMessage>
                muxQueue = new LinkedBlockingQueue<ServicePlatformMessage>(),
                dispatcherQueue = new LinkedBlockingQueue<ServicePlatformMessage>();
        TestProducer producer = new TestProducer(muxQueue, this);
        consumer = new TestConsumer(dispatcherQueue);
        AdaptorCore core = new AdaptorCore(muxQueue, dispatcherQueue, consumer, producer, 0.1);
        core.start();

        // Ensure AdaptorCore is running
        int counter = 0;
        while (counter < 2) {
            if (lastHeartbeat.contains("RUNNING")) {
                counter++;
            }
            Thread.sleep(1000);
        }

        // Read descriptors
        mapper = SonataManifestMapper.getSonataMapper();
        networkServiceDescriptor = mapper.readValue(getRawDescriptor(new File("./YAML/fw-vtc-nsd.yml")), ServiceDescriptor.class);
        vfwVnfd = mapper.readValue(new File("./YAML/fw-vnf-vnfd.yml"), VnfDescriptor.class);
        vtcVnfd = mapper.readValue(new File("./YAML/vtc-vnfd.yml"), VnfDescriptor.class);

        // put everything together
        this.nsdPayload = new ServiceDeployPayload();
        nsdPayload.setServiceDescriptor(networkServiceDescriptor);
        nsdPayload.addVnfDescriptor(vtcVnfd);
        nsdPayload.addVnfDescriptor(vfwVnfd);
    }

    /**
     * Whereas this is no real unit test, but an integration test, this method is used to structure the order of
     * execution during the test. The generated data of the containing steps is returned and used for calling the
     * following methods in order to make the data dependencies easier to understand.
     * - configureNetwork: We want to provide our registered VIMEmuNetworkWrapper with the serice paths.
     * To best of my knowledge, the intended process should be like follows: Assemble a NetworkConfigurationPayload,
     * containing the NetworkServiceDescriptor, the VNFd's and the VNFr's. In order to emulate ServicePlatforms
     * behaviour, we need to extend the VNFd with an Instance_UUID. This UUID is used by the
     * ConfigureNetworkCallProcessor in order to find the corresponding Compute-Wrapper and consequently the
     * NetworkWrapper: Instance_UUID -> computeVimUuid -> networkVimUuid.
     * In the end, the NetworkWrapper is invoked with the corresponding forwarding graph.
     * The entry function_instances is added during:
     * @see sonata.kernel.vimadaptor.wrapper.vimemu.VIMEmuComputeWrapper#deployFunction(FunctionDeployPayload, String)
     *
     *
     */
    @Test
    public void test() throws IOException, InterruptedException {
        System.out.println("[EmulatorTest] Adding PoP 1");
        String computeWrapperUUID = registerComputeVIM();
        System.out.println("[EmulatorTest] Register Network Wrapper for VIMEmu");
        String networkWrapperUUID = registerNetworkVIM(computeWrapperUUID);
        System.out.println("[EmulatorTest] Listing available VIMs.");
        listVIMs();
        System.out.println("[EmulatorTest] Prepare for service deployment.");
        prepare(computeWrapperUUID);
        System.out.println("[EmulatorTest] Deploy Functions");
        deployFunctions(computeWrapperUUID);
        System.out.println("[EmulatorTest] Configure Network");
        configureNetwork(networkWrapperUUID);
    }

    private String getRawDescriptor(File inputDescriptor) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        BufferedReader in = new BufferedReader(new InputStreamReader(
                new FileInputStream(inputDescriptor), Charset.forName("UTF-8")));
        String line;
        while ((line = in.readLine()) != null)
            stringBuilder.append(line + "\n\r");
        return stringBuilder.toString();
    }

    /**
     * This registers VIM-Emu to VIM-Adaptor. The registering takes place in the postgres database. The concrete UUID is
     * important in order to adress the correct wrapper for the following actions.
     *
     * @throws IOException
     * @throws InterruptedException
     */

    public String registerComputeVIM() throws IOException, InterruptedException {
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
        String rawAnswer = sendServicePlatformMessage(addVimMessage);
        JSONTokener tokener = new JSONTokener(rawAnswer);
        JSONObject jsonObject = (JSONObject) tokener.nextValue();
        String status = jsonObject.getString("request_status");
        String computeWrapperUuid = jsonObject.getString("uuid");
        Assert.assertTrue(status.equals("COMPLETED"));
        System.out.println("VIM-Emu Wrapper added, with uuid: " + computeWrapperUuid);
        return computeWrapperUuid;
    }

    private String registerNetworkVIM(String computeWrapperUUID) throws InterruptedException {
        String addNetVimBody = "{\"vim_type\":\"VIMEmu\", " + "\"name\":\"vim-emu\","
                + "\"vim_address\":\"127.0.0.1\",\"username\":\"username\",\"city\":\"Paderborn\",\"country\":\"Germany\",\"domain\":\"bla\","
                + "\"pass\":\"password\",\"configuration\":{\"compute_uuid\":\"" + computeWrapperUUID + "\"}}";
        String topic = "infrastructure.management.network.add";
        ServicePlatformMessage addNetVimMessage = new ServicePlatformMessage(addNetVimBody,
                "application/json", topic, UUID.randomUUID().toString(), topic);
        String rawAnswer = sendServicePlatformMessage(addNetVimMessage);
        JSONTokener tokener = new JSONTokener(rawAnswer);
        JSONObject jsonObject = (JSONObject) tokener.nextValue();
        String status = jsonObject.getString("request_status");
        String netWrUuid = jsonObject.getString("uuid");
        System.out.println("OVS Wrapper added, with uuid: " + netWrUuid);
        System.out.println("Status:\n" + status);
        return netWrUuid;
    }

    public void listVIMs() throws InterruptedException, IOException {
        String topic = "infrastructure.management.compute.list";
        ServicePlatformMessage listVimMessage =
                new ServicePlatformMessage(null, null, topic, UUID.randomUUID().toString(), topic);
        String vimListRaw = sendServicePlatformMessage(listVimMessage);
        System.out.println("List of VIMs");
        System.out.println(vimListRaw);
        VimResources[] vimList = mapper.readValue(vimListRaw, VimResources[].class);
        System.out.println("[TwoPoPTest] Listing available PoP");
        for (VimResources resource : vimList) {
            System.out.println(mapper.writeValueAsString(resource));
        }
        output = null;
    }

    /**
     * According to the documentation, this method should of NFVI-PoP (?) for the deployment of a NS -> Network Service
     * - Predeploying VNF images (Glance, Docker)
     * - Creating network facilities to which VNFs will be attached
     *
     * @param computeWrapperUUID
     * @throws JsonProcessingException
     * @throws InterruptedException
     */
    public void prepare(String computeWrapperUUID) throws JsonProcessingException, InterruptedException {
        // Prepare the system for a service deployment -> very generic (ann. of ftc)

        ServicePreparePayload payload = new ServicePreparePayload();

        payload.setInstanceId(nsdPayload.getNsd().getInstanceUuid()); // Not sure, for what this NSD is used, because it is
        // related to our deployment situation, see setUp()
        ArrayList<VimPreDeploymentList> vims = new ArrayList<>();
        VimPreDeploymentList vimDepList = new VimPreDeploymentList();
        vimDepList.setUuid(computeWrapperUUID);
        ArrayList<VnfImage> vnfImages = new ArrayList<>();
        VnfImage vnfImage1 =
                new VnfImage("proxy-squid-img",
                        "file://./images/eu.sonata-nfv_squid-vnf_0.1_1");
        VnfImage vnfImage2 =
                new VnfImage("l4fw-socat-img",
                        "file://./images/eu.sonata-nfv_socat-vnf_0.1_1");
        VnfImage vnfImage3 =
                new VnfImage("http-apache-img",
                        "file://./images/eu.sonata-nfv_apache-vnf_0.1_1");

        vnfImages.add(vnfImage1);
        vnfImages.add(vnfImage2);
        vnfImages.add(vnfImage3);

        vimDepList.setImages(vnfImages);
        vims.add(vimDepList);

        payload.setVimList(vims);

        String body = mapper.writeValueAsString(payload);
        System.out.println("[EmulatorTest] Request body:");
        System.out.println(body);

        String topic = "infrastructure.service.prepare";
        ServicePlatformMessage servicePrepareMessage = new ServicePlatformMessage(body,
                "application/x-yaml", topic, UUID.randomUUID().toString(), topic);

        String rawAnswer = sendServicePlatformMessage(servicePrepareMessage);

        JSONTokener tokener = new JSONTokener(rawAnswer);
        JSONObject jsonObject = (JSONObject) tokener.nextValue();
        String status = jsonObject.getString("request_status");
        String message = jsonObject.getString("message");
        Assert.assertTrue("Failed to prepare the environment for the service deployment: " + status
                + " - message: " + message, status.equals("COMPLETED"));
        System.out.println("Service " + payload.getInstanceId() + " ready for deployment");
    }

    public void deployFunctions(String computeWrapperUUID) throws IOException, InterruptedException {
        // Deploy each of the VNFs
        vnfRecords = new ArrayList<>();
        // Deploy fw
        for(VnfDescriptor vnfd : new ArrayList<>(Arrays.asList(vfwVnfd, vtcVnfd))){
            String rawAnswer = sendServicePlatformMessage(assembleFunctionDeploymentPayload(vnfd, computeWrapperUUID));
            Assert.assertNotNull(rawAnswer);
            int retry = 0, maxRetry = 2;
            while (rawAnswer.contains("heartbeat") || rawAnswer.contains("Vim Added") && retry < maxRetry) {
                waitOnOutput();
                retry++;
            }
            System.out.println("FunctionDeployResponse: ");
            System.out.println(rawAnswer);
            Assert.assertTrue("No response received after function deployment", retry < maxRetry);
            FunctionDeployResponse response = mapper.readValue(rawAnswer, FunctionDeployResponse.class);
            Assert.assertTrue(response.getRequestStatus().equals("COMPLETED"));
            Assert.assertTrue(response.getVnfr().getStatus() == Status.normal_operation);
            vnfRecords.add(response.getVnfr());
        }
        // Deploy vtc
        /* At this point, the VNFs should be deployed correctly. Next: Configure intra-PoP chaining
        topic: infrastructure.chain.configure
        data: { service_instance_id: String, nsd: SonataNSDescriptor, vnfds: [{ SonataVNFDescriptor }],
         vnfrs: [{ SonataVNFRecord }], ingresses: [{ location:String, nap:x.x.x.x/y }],
          egresses: [{ location:String, nap:x.x.x.x/y }] }
          */


    }

    /**
     * Assembles a FunctionDeployMessage with the necessary information
     * @param vnfd Function to deploy
     * @param computeWrapperUUID Compute Wrapper handling the deployed service
     * @return Assembled FunctionDeployMessage
     * @throws JsonProcessingException
     */
    private ServicePlatformMessage assembleFunctionDeploymentPayload(VnfDescriptor vnfd, String computeWrapperUUID) throws JsonProcessingException {
        FunctionDeployPayload vnfPayload = new FunctionDeployPayload();
        vnfPayload.setVnfd(vnfd);
        vnfPayload.setVimUuid(computeWrapperUUID);
        vnfPayload.setServiceInstanceId(nsdPayload.getNsd().getInstanceUuid());
        String body = mapper.writeValueAsString(vnfPayload);
        String topic = "infrastructure.function.deploy";
        ServicePlatformMessage functionDeployMessage = new ServicePlatformMessage(body,
                "application/x-yaml", topic, UUID.randomUUID().toString(), topic);
        return functionDeployMessage;
    }

    /**
     * @param servicePlatformMessage
     */
    private String sendServicePlatformMessage(ServicePlatformMessage servicePlatformMessage) throws InterruptedException {
        consumer.injectMessage(servicePlatformMessage);
        Thread.sleep(2000);
        waitOnOutput();
        String returnValue = output;
        output = null;
        return returnValue;
    }

    public void configureNetwork(String networkWrapperUUID) throws JsonProcessingException, InterruptedException {
        NetworkConfigurePayload networkConfigurePayload = new NetworkConfigurePayload();
        networkConfigurePayload.setNsd(networkServiceDescriptor);
        networkConfigurePayload.setServiceInstanceId("123");
        networkConfigurePayload.setVnfds(new ArrayList<>(Arrays.asList(vtcVnfd, vfwVnfd)));
        networkConfigurePayload.setVnfrs(vnfRecords);

        String body = mapper.writeValueAsString(networkConfigurePayload);
        String topic = "infrastructure.service.chain.configure";
        ServicePlatformMessage functionDeployMessage = new ServicePlatformMessage(body,
                "application/x-yaml", topic, UUID.randomUUID().toString(), topic);
        String rawAnswer = sendServicePlatformMessage(functionDeployMessage);
        Assert.assertNotNull(rawAnswer);
        int retry = 0, maxRetry = 2;
        while (rawAnswer.contains("heartbeat") || rawAnswer.contains("Vim Added") && retry < maxRetry) {
            waitOnOutput();
            retry++;
        }
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