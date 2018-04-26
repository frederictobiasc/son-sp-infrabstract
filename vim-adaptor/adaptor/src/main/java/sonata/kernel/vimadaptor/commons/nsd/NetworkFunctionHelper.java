package sonata.kernel.vimadaptor.commons.nsd;

import javassist.NotFoundException;
import sonata.kernel.vimadaptor.commons.VnfRecord;
import sonata.kernel.vimadaptor.commons.vnfd.VnfDescriptor;

import java.util.List;

public class NetworkFunctionHelper {
    public static String getVnfNameById(List<NetworkFunction> networkFunctions, String id) throws NotFoundException {
        for (NetworkFunction networkFunction : networkFunctions) {
            if (networkFunction.getVnfId().equals(id)) {
                return networkFunction.getVnfName();
            }
        }
        throw new NotFoundException("Network Function with ID: " + id + " was not found");
    }

    public static VnfDescriptor getVnfdByName(List<VnfDescriptor> vnfDescriptors, String name) throws NotFoundException {
        for (VnfDescriptor vnfDescriptor : vnfDescriptors) {
            if (vnfDescriptor.getName().equals(name)) {
                return vnfDescriptor;
            }
        }
        throw new NotFoundException("VNFD with name: " + name + " was not found");
    }

    public static VnfRecord getVnfrByVnfdReference(List<VnfRecord> vnfRecords, String vnfdReference) throws NotFoundException {
        for (VnfRecord vnfRecord : vnfRecords) {
            if (vnfRecord.getDescriptorReference().equals(vnfdReference)) {
                return vnfRecord;
            }
        }
        throw new NotFoundException("No vnfr found for: " + vnfdReference);
    }

}
