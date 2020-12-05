/*******************

Team members and IDs:
Aldo Arturo Ortega Yucra 6156151

Github link:
https://github.com/Alopalao/Project3

*******************/

package net.floodlightcontroller.myrouting;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.PriorityQueue;

import java.util.ArrayList;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.SwitchPort;

import java.util.ArrayList;
import java.util.Set;

import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.linkdiscovery.LinkInfo;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.routing.RouteId;
import net.floodlightcontroller.staticflowentry.IStaticFlowEntryPusherService;
import net.floodlightcontroller.topology.NodePortTuple;

import org.openflow.util.HexString;
import org.openflow.util.U8;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyRouting implements IOFMessageListener, IFloodlightModule {

	protected IFloodlightProviderService floodlightProvider;
	protected Set<Long> macAddresses;
	protected static Logger logger;
	protected IDeviceService deviceProvider;
	protected ILinkDiscoveryService linkProvider;

	protected Map<Long, IOFSwitch> switches; //ID switches and switches
	protected Map<Link, LinkInfo> links; //connections
	protected Collection<? extends IDevice> devices;

	protected static int uniqueFlow;
	protected ILinkDiscoveryService lds; //list of links??
	protected IStaticFlowEntryPusherService flowPusher;
	protected boolean printedTopo = false;
	
	protected HashMap<Long, HashMap<Long, Long>> topo = new HashMap<Long, HashMap<Long, Long>>();//<src, <dst, cost>>
	//protected PriorityQueue<HashMap<Long, Long>> result = new PriorityQueue<HashMap<Long, Long>>();
	protected ArrayList<Long> result;

	@Override
	public String getName() {
		return MyRouting.class.getSimpleName();
	}
	
	
	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		return (type.equals(OFType.PACKET_IN)
				&& (name.equals("devicemanager") || name.equals("topology")) || name
					.equals("forwarding"));
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		return false;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		return null;
	}

	@Override
	//Do nothing here
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l 
		= new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		l.add(IDeviceService.class);
		l.add(ILinkDiscoveryService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider = context
				.getServiceImpl(IFloodlightProviderService.class);
		deviceProvider = context.getServiceImpl(IDeviceService.class);
		linkProvider = context.getServiceImpl(ILinkDiscoveryService.class);
		flowPusher = context
				.getServiceImpl(IStaticFlowEntryPusherService.class);
		lds = context.getServiceImpl(ILinkDiscoveryService.class);

	}

	@Override
	public void startUp(FloodlightModuleContext context) {
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
	}

	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		// Print the topology if not yet.
		if (!printedTopo) {
			System.out.println("*** Print topology");
			// For each switch, print its neighbor switches.
			switches = floodlightProvider.getAllSwitchMap();
			links = lds.getLinks();
			
			
			for(Map.Entry<Long, IOFSwitch> aux : switches.entrySet()) {
				
				Long src = aux.getValue().getId();
				topo.put(src, new HashMap<Long, Long>());
				System.out.print("switch " + src + " neighbors: ");
				
				for(Map.Entry<Link, LinkInfo> linkaux : links.entrySet()) {
					Long dst = linkaux.getKey().getDst();
					if(src == linkaux.getKey().getSrc()) {
						Long w;
						if((src.intValue() % 2 == 0) && (dst.intValue() % 2 == 0)) {
							w = Long.valueOf(100);
						}
						else if((src.intValue() % 2 != 0) && (dst.intValue() % 2 != 0)) {
							w = Long.valueOf(1);
						}
						else{
							w = Long.valueOf(10);
						}
						System.out.print(dst + " ");
						topo.get(src).put(dst, w);
					}
				}
				System.out.println();
			}
			
			printedTopo = true;
		}


		// eth is the packet sent by a switch and received by floodlight.
		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx,
				IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

		// We process only IP packets of type 0x0800.
		if (eth.getEtherType() != 0x0800) {
			return Command.CONTINUE;
		}
		else{
			System.out.println("*** New flow packet");

			// Parse the incoming packet.
			OFPacketIn pi = (OFPacketIn)msg;
			OFMatch match = new OFMatch();
		    match.loadFromPacket(pi.getPacketData(), pi.getInPort());	
			// Obtain source and destination IPs.
			// ...
		    String srcIP = IPv4.fromIPv4Address(match.getNetworkSource());
		    String dstIP = IPv4.fromIPv4Address(match.getNetworkDestination());
		    System.out.println("srcIP: " + srcIP);
		    System.out.println("dstIP: " + dstIP);
		    
			// Calculate the path using Dijkstra's algorithm.
			Long src = Long.valueOf(Character.getNumericValue(srcIP.charAt(7)));
			Long dst = Long.valueOf(Character.getNumericValue(dstIP.charAt(7)));
		    predijkstra();
		    dijkstra(src, dst, result);
		    
		    System.out.print("Path: ");
		    for(int i = 0; i < result.size(); i++) {
		    	System.out.print(result.get(i) + " ");
		    }
		    System.out.println();
		    
		    RouteId id = new RouteId(src, dst);
		    
		    List<NodePortTuple> switchPorts = new ArrayList<>();
		    for(int i = 0; i < result.size() - 1; i++) {
		    	Link link = findLink(i);
		    	NodePortTuple node1 = new NodePortTuple(link.getSrc(), link.getSrcPort());
		    	NodePortTuple node2 = new NodePortTuple(link.getDst(), link.getDstPort());
		    	switchPorts.add(node1);
		    	switchPorts.add(node2);
		    }
		    
			Route route = new Route(id, switchPorts);

			// Write the path into the flow tables of the switches on the path.
			if (route != null) {	
				installRoute(route.getPath(), match);
				System.out.println("Path Installed");
			}
			
			return Command.STOP;
		}
	}

	private void installRoute(List<NodePortTuple> path, OFMatch match) {

		OFMatch m = new OFMatch();

		m.setDataLayerType(Ethernet.TYPE_IPv4)
				.setNetworkSource(match.getNetworkSource())
				.setNetworkDestination(match.getNetworkDestination());

		for (int i = 0; i <= path.size() - 1; i += 2) {
			short inport = path.get(i).getPortId();
			m.setInputPort(inport);
			List<OFAction> actions = new ArrayList<OFAction>();
			OFActionOutput outport = new OFActionOutput(path.get(i+1).getPortId());
			actions.add(outport);

			OFFlowMod mod = (OFFlowMod) floodlightProvider
					.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
			mod.setCommand(OFFlowMod.OFPFC_ADD)
					.setIdleTimeout((short) 0)
					.setHardTimeout((short) 0)
					.setMatch(m)
					.setPriority((short) 105)
					.setActions(actions)
					.setLength(
							(short) (OFFlowMod.MINIMUM_LENGTH 
									+ OFActionOutput.MINIMUM_LENGTH));
			flowPusher.addFlow("routeFlow" + uniqueFlow, mod,
					HexString.toHexString(path.get(i).getNodeId()));
			uniqueFlow++;
		}
	}
	
	private void predijkstra() {
		result = new ArrayList<Long>();
	}
	
	private void dijkstra(Long src, Long dst, ArrayList<Long> result) {
		Boolean ready = false;
		Long got = null;
		Long path = Long.valueOf(Integer.MAX_VALUE);
		for(Entry<Long, HashMap<Long, Long>> aux : topo.entrySet()) {
			if(aux.getKey() == src) {
				result.add(aux.getKey());
				for(Map.Entry<Long, Long> aux2 : topo.get(aux.getKey()).entrySet()) {
					if(aux2.getKey() == dst) {
						result.add(aux2.getKey());
						ready = true;
						break;
					}
					else if(aux2.getValue() < path) {
						got = aux2.getKey();
						path = aux2.getValue();
					}
				}
			}
		}
		if(!ready) {
			dijkstra(got, dst, result);
		}
	}
	
	private Link findLink(int pos) {
		for(Link aux: lds.getSwitchLinks().get(result.get(pos))) {
			if(aux.getSrc() == result.get(pos)) {
				if(aux.getDst() == result.get(pos+1)) {
					return aux;
				}
			}
		}
		return null;
	}
	
}