import org.json.JSONObject;

import javax.sound.sampled.LineUnavailableException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.sql.*;
import java.util.ArrayList;

public class RegisterServer {
    private static boolean isAppRunning = true;
    private static int portNumbers = 9000,audioSenderPort;
    private static ArrayList<String> pictureClients;
    private static ArrayList<String> audioClients;
    private static InetAddress audioServerInet;

    public static void main(String[] args) throws SocketException {
	// write your code here
        pictureClients = new ArrayList<>();
        audioClients = new ArrayList<>();
        final DatagramSocket socket = new DatagramSocket(5656);
        byte[] buffer = new byte[2048];
        final DatagramPacket packet = new DatagramPacket(buffer,0,buffer.length);
        //register thread
        new Thread()
        {
            public void run()
            {
                while (isAppRunning)
                {
                    try {

                        Class.forName("com.mysql.jdbc.Driver");
                        Connection con= DriverManager.getConnection(
                                "jdbc:mysql://localhost:3306/skylearning","root","");
                        socket.receive(packet);
                        byte[] data = packet.getData();
                        String json = new String(data,0,data.length);
                        JSONObject jsonObject = new JSONObject(json.trim());
                        String as = jsonObject.getString("as");
                        String city = jsonObject.getString("city");
                        String country = jsonObject.getString("country");
                        String countryCode = jsonObject.getString("countryCode");
                        String isp = jsonObject.getString("isp");
                        String org = jsonObject.getString("org");
                        String lat = jsonObject.getDouble("lat")+"";
                        String lon = jsonObject.getDouble("lon")+"";
                        String query = jsonObject.getString("query");
                        String region = jsonObject.getString("region");
                        String regionName = jsonObject.getString("regionName");
                        String timezone = jsonObject.getString("timezone");
                        String zip = jsonObject.getString("zip");
                        String publicIp = packet.getAddress().getHostAddress();
                        String privateIp = jsonObject.getString("private_ip");
                        String deviceId = jsonObject.getString("device_id");
                        String type = jsonObject.getString("type");

                        Statement stmt=con.createStatement();
                        ResultSet rs=stmt.executeQuery("select * from peers where device_id='"+deviceId+"'");
                        boolean isValueExists = rs.first();
                        if(!type.equals("audio") && !type.equals("video"))
                        if(isValueExists)
                        {

                            long millis = System.currentTimeMillis();
                            String qry = "update peers set timestamp='"+millis+"',ping_port='"+packet.getPort() +"' where device_id='"+deviceId+"'";
                            stmt.executeUpdate(qry);

                        }
                        else
                        {
                            long millis = System.currentTimeMillis();
                            String qry = "insert into peers(device_id,`as`,city,country,country_code,isp,lat,lng,org,query,region,regionName,timezone,zip,public_ip,private_ip,timestamp,ping_port) values('"+deviceId+"','"+as+"','"+city+"','"
                                    +country+"','"+countryCode+"','"+isp+"','"+lat+"','"+lon+"','"+
                                    org+"','"+query+"','"+region+"','"+regionName+"','"+timezone+
                                    "','"+zip+"','"+publicIp+"','"+privateIp+"','"+millis+"','"+packet.getPort()+"')";
                            stmt.executeUpdate(qry);
                        }
                        if(type.equals("audio"))
                        {
                            String privatePort = jsonObject.getString("private_port");
                            String qry = "update peers set audio_port_public='"+packet.getPort()+"',audio_port_private='"+privatePort+"' where device_id='"+deviceId+"'";
                            stmt.executeUpdate(qry);
                        }

                        if(type.equals("video"))
                        {
                            String privatePort = jsonObject.getString("private_port");
                            String qry = "update peers set video_port_public='"+packet.getPort()+"',video_port_private='"+privatePort+"' where device_id='"+deviceId+"'";
                            stmt.executeUpdate(qry);
                        }
                        con.close();



                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }

            }

        }.start();
        final DatagramSocket connectionRequestSocket = new DatagramSocket(5657);
        byte[] buffers = new byte[2048];
        final DatagramPacket connectionRequestPacket = new DatagramPacket(buffers,0,buffers.length);


        new Thread()
        {
            public void run()
            {
                while(isAppRunning)
                {
                    try {
                        connectionRequestSocket.receive(connectionRequestPacket);
                        String jsonResponse = new String(connectionRequestPacket.getData(),0,connectionRequestPacket.getLength());
                        JSONObject object = new JSONObject(jsonResponse);
                        //String toDevice = object.getString("to_device");
                        String fromDevice = object.getString("device_id");
                        String senderprivateAddress = object.getString("private_socket");
                        String type = object.getString("type");
                        Class.forName("com.mysql.jdbc.Driver");
                        Connection con= DriverManager.getConnection(
                                "jdbc:mysql://localhost:3306/skylearning","root","");
                        Statement statement = con.createStatement();
                        if(type.equals("conn_req"))
                        {

                            String query = "select * from peers where device_id!='"+fromDevice+"'";
                            ResultSet rs = statement.executeQuery(query);

                            if(rs.last())
                            {
                                String pubIp = rs.getString("public_ip");
                                String pingPort = rs.getString("ping_port");
                                String qry = "select * from peers where device_id='"+fromDevice+"'";
                                ResultSet res = statement.executeQuery(qry);
                                res.first();

                                String receiverPrivateIp = res.getString("private_ip");
                                String receiverPrivatePortVideo = res.getString("video_port_private");
                                String receiverPublicPortVideo = res.getString("video_port_public");
                                String receiverPrivatePortAudio = res.getString("audio_port_private");
                                String receiverPublicPortAudio = res.getString("audio_port_public");

                                JSONObject respobject = new JSONObject();
                                respobject.put("type","send_to");
                                respobject.put("public_endpoint_video",connectionRequestPacket.getAddress().getHostAddress()+":"+receiverPublicPortVideo);
                                respobject.put("private_endpoint_video",receiverPrivateIp+":"+receiverPrivatePortVideo);
                                respobject.put("public_endpoint_audio",connectionRequestPacket.getAddress().getHostAddress()+":"+receiverPublicPortAudio);
                                respobject.put("private_endpoint_audio",receiverPrivateIp+":"+receiverPrivatePortAudio);
                                String resonse = respobject.toString();
                                System.out.println(respobject.toString());
                                DatagramPacket responsePacket = new DatagramPacket(resonse.getBytes(),0,resonse.getBytes().length,InetAddress.getByName(pubIp),Integer.parseInt(pingPort));
                                socket.send(responsePacket);


                            }
                            else
                            {
                                String qry = "select * from peers where device_id='"+fromDevice+"'";
                                ResultSet res = statement.executeQuery(qry);
                                res.first();
                                String publicVideoPort = res.getString("video_port_public");
                                String publicAudioPort = res.getString("audio_port_public");
                                String pingPortPublic = res.getString("ping_port");
                                audioClients.add(connectionRequestPacket.getAddress().getHostAddress()+"_"+publicAudioPort);
                                pictureClients.add(connectionRequestPacket.getAddress().getHostAddress()+"_"+publicVideoPort);
                                JSONObject tmp = new JSONObject();
                                tmp.put("type","receive_ack");
                                tmp.put("connection","Relaying");
                                String st = tmp.toString();
                                DatagramPacket pack = new DatagramPacket(st.getBytes(),0,st.getBytes().length,connectionRequestPacket.getAddress(),connectionRequestPacket.getPort());
                                connectionRequestSocket.send(pack);
                                System.out.println("sent");



                            }

                        }
                        else
                        {
                            String response = object.getString("message");
                            //DatagramPacket responsePacket = new DatagramPacket(response.getBytes(),0,response.getBytes().length,InetAddress.getByName(pubIp),Integer.parseInt(pingPort));
                            //socket.send(responsePacket);
                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }

                }
            }


        }.start();

        final DatagramSocket pictureSocket = new DatagramSocket(6699);


        new Thread()
        {
            public void run()
            {
                byte[] picBuff = new byte[20000];

                DatagramPacket picturePacket = new DatagramPacket(picBuff,0,picBuff.length);
                while (true)
                {
                    try {
                        //System.out.println("received");
                        pictureSocket.receive(picturePacket);
                        for(int i=0;i<pictureClients.size();i++)
                        {
                            String address = pictureClients.get(i);
                            String[] ipPort = address.split("_");
                            picturePacket.setAddress(InetAddress.getByName(ipPort[0]));
                            picturePacket.setPort(Integer.parseInt(ipPort[1]));
                            socket.send(picturePacket);
                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }


            }


        }.start();

        final DatagramSocket audioSocket = new DatagramSocket(6700);

        new Thread()
        {
            public void run()
            {
                byte[] audBuff = new byte[20000];
                boolean fTime = true;

                DatagramPacket audioPacket = new DatagramPacket(audBuff,0,audBuff.length);
                while (true)
                {
                    try {
                        //System.out.println("received");
                        audioSocket.receive(audioPacket);
                        //AudioClient.toSpeaker(audioPacket.getData());
                        if(fTime)
                        {
                            audioServerInet = audioPacket.getAddress();
                            audioSenderPort = audioPacket.getPort();
                            fTime = false;
                        }

                        for(int i=0;i<audioClients.size();i++)
                        {

                            String address = audioClients.get(i);
                            String[] ipPort = address.split("_");
                            audioPacket.setAddress(InetAddress.getByName(ipPort[0]));
                            audioPacket.setPort(Integer.parseInt(ipPort[1]));
                            socket.send(audioPacket);


                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }


            }


        }.start();


        final DatagramSocket caudioSocket = new DatagramSocket(4114);
        new Thread()
        {
            public void run()
            {
                byte[] audBuff = new byte[2048];

                DatagramPacket audioPacket = new DatagramPacket(audBuff,0,audBuff.length);
                while (true)
                {
                    try {
                        //System.out.println("received");
                        caudioSocket.receive(audioPacket);
                        //AudioClient.toSpeaker(audioPacket.getData());
                        audioPacket.setAddress(audioServerInet);
                        audioPacket.setPort(audioSenderPort);
                        audioSocket.send(audioPacket);
                       // System.out.println("sasas");

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }


            }


        }.start();







    }


}
