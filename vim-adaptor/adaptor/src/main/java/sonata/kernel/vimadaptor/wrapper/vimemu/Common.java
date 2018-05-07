package sonata.kernel.vimadaptor.wrapper.vimemu;

import java.util.List;

public class Common {
    static String translateToVimVduId(String vnfName, String vduId){
        return vnfName +"_"+ vduId;
    }
    static String translateToVimVduNetworkInterface(String vimVduId, String vduInterface){
        int i = (vimVduId + vduInterface).hashCode();

        return Long.toString(i);
    }

    /**
     * @param vimVduId must contain the vimVduId that were originally passed to {@link #translateToVimVduNetworkInterface(String, String)}
     * @param connectionPoints must contain the connectionPoints which where originally passed to
     * {@link #translateToVimVduNetworkInterface(String, String)}
     * @param vimVduNetworkInterface is an identifier build by
     * {@link #translateToVimVduNetworkInterface(String, String)}
     * @return Original connectionPoint that was hashed to vimVduNetworkInterface
     */
    static String mapVimVduNetworkInterfaceToConnectionPoint(String vimVduId, List<String> connectionPoints, String vimVduNetworkInterface){
        for(String connectionPoint:connectionPoints){
            if(translateToVimVduNetworkInterface(vimVduId, connectionPoint).equals(vimVduNetworkInterface)){
                return connectionPoint;
            }
        }
        throw new IllegalArgumentException("Since connectionPoints of a vdu are hashed to vimVduId, here should be at least one match.");
    }
}
