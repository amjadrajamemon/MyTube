package com.novastream.iptv.core;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.util.LinkedHashSet;

/** Credential-free DNS / TCP diagnostic for a provider's configured server. */
public final class PortalConnectivityProbe {
    private PortalConnectivityProbe(){}

    public static URI hostOnly(String input){
        if(input==null||input.trim().isEmpty())throw new IllegalArgumentException("Enter a playlist or portal URL");
        try{
            URI original=new URI(input.trim());
            String host=original.getHost();
            String scheme=original.getScheme();
            if(host==null||scheme==null||(!scheme.equalsIgnoreCase("https")&&!scheme.equalsIgnoreCase("http")))
                throw new IllegalArgumentException("A full HTTP or HTTPS playlist URL is required");
            // Deliberately discard user info, path, query and fragment: may contain passwords.
            return new URI(scheme,null,host,original.getPort(),"/",null,null);
        }catch(java.net.URISyntaxException e){
            throw new IllegalArgumentException("Malformed playlist host and port");
        }
    }

    public static int[] probePorts(URI host){
        LinkedHashSet<Integer> ports=new LinkedHashSet<>();
        ports.add(host.getPort()>0?host.getPort():("https".equalsIgnoreCase(host.getScheme())?443:80));
        // Test only the same hostname, never send credentials or playlist paths.
        ports.add(80);
        ports.add(443);
        int[] results=new int[ports.size()];int i=0;for(Integer port:ports)results[i++]=port;
        return results;
    }

    public static String check(String input){
        URI uri=hostOnly(input);
        String hostname=uri.getHost();
        StringBuilder report=new StringBuilder("Credential-free network diagnosis\n\n");
        report.append("Host: ").append(hostname).append("\n");
        int[] ports=probePorts(uri);
        try{
            InetAddress[] addresses=InetAddress.getAllByName(hostname);
            if(addresses.length==0)return report.append("DNS: no addresses returned.").toString();
            report.append("DNS: resolved ").append(addresses.length).append(" address(es)\n");
            int maxAddresses=Math.min(2,addresses.length);
            for(int port:ports){
                boolean reachable=false;
                for(int i=0;i<maxAddresses;i++){
                    try(Socket socket=new Socket()){
                        socket.connect(new InetSocketAddress(addresses[i],port),1700);
                        reachable=true;break;
                    }catch(IOException ignored){
                        // Keep the response free of provider URLs and local/network IP details.
                    }
                }
                report.append("TCP port ").append(port).append(": ")
                      .append(reachable?"reachable":"not reachable within the probe limit").append("\n");
            }
        }catch(java.net.UnknownHostException e){
            report.append("DNS: hostname cannot be resolved on this network.\n");
        }catch(SecurityException e){
            report.append("Network test blocked by Android permissions or restrictions.\n");
        }
        report.append("\nThis checks only DNS and TCP access: it does not log in or verify playlist content. ");
        report.append("A working port 80/443 does not mean your IPTV provider serves the same subscription there. ");
        report.append("Check the exact source protocol and port used by the working player; do not change ports blindly. ");
        report.append("If the configured port fails on Wi-Fi, compare using mobile data.");
        return report.toString();
    }
}
