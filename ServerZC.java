import java.io.*;
import java.net.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.List;



public class Server {
    static int PORT = 49230; // พอร์ตที่เซิร์ฟเวอร์จะทำงาน
    static String FILES_DIR = "C:\Users\smamm\OneDrive\Desktop\VdThreads"; // ไดเร็กทอรีที่เก็บไฟล์ที่เซิร์ฟเวอร์สามารถส่งให้กับลูกค้าได้

    public static void main(String[] args) {
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(PORT); //สร้าง Socket รับส่งข้อมูล
            System.out.println("Server started on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept(); //รอรับการเชื่อมต่อจาก client
                Thread clientHandler = new ClientHandler(clientSocket); //เมื่อ client ติดต่อมาก็สร้าง Thread 
                clientHandler.start(); //ใช้ Object clientHandler เรียกใช้ start เพื่อเริ่มการทำงาน
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static class ClientHandler extends Thread { // class thread จัดการการเชื่อมต่อของ client
        private Socket clientSocket;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket; //รับ socket ของ client มา
        }

        @Override
        public void run() { //สร้าง Object 2 ตัวคือ dis และ dos 
            try (DataInputStream dis = new DataInputStream(clientSocket.getInputStream()); //dis เป็น object ที่สร้างเพื่ออ่านข้อมูลจาก client ที่ส่งมายัง Server
                 DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream())) { //dos เป็น object ที่สร้างเพื่อเขียนข้อมูลจาก server ไปสู่ client 
                //ที่ใช้  DataOutputStream เพราะมีเมธอดเฉพาะอย่าง readInt(), readFloat(), readUTF() ซึ่งช่วยในการอ่านข้อมูลพื้นฐานโดยตรง ไม่ต้องแปลงข้อมูลเพิ่มเติมเอง
                
                // ส่งรายชื่อไฟล์ไปยังลูกค้า
                File directory = new File(FILES_DIR); //สร้าง object directory ของ didrectory ที่ใช้เก็บไฟล์ video ของฝั่ง server
                File[] files = directory.listFiles(); //อ่านไฟล์ที่มีใน didrectory ของฝั่ง server ลงในตัวแปร files ที่เป็นอาเรย์
                List<String> fileNames = new ArrayList<>();
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile()) {
                            fileNames.add(file.getName()); //เก็บชื่อไฟล์ทั้งหมดไว้ใน ตัวแปร fileNames ที่เป็นอาเรย์ลิสต์
                        }
                    }
                }
                dos.writeInt(fileNames.size());
                for (String fileName : fileNames) {
                    dos.writeUTF(fileName); //ใช้ object dos เขียน(ส่ง)ชื่อไฟล์ที่มีใน server ไปให้ client
                }

                // รับชื่อไฟล์ที่ลูกค้าต้องการและวิธีการส่ง
                String requestedFileName = dis.readUTF(); //ตัวแปร  requestedFileName เก็บชื่อไฟล์ที่ client ต้องการ โดยการอ่านผ่าน object dis
                int transferMethod = dis.readInt(); // ตัวแปร transferMethod เก็บวิธีที่ client ต้องการใช้รับส่งไฟล์ โดยการอ่านผ่าน object dis
                System.out.println("Client requested file: " + requestedFileName);

                File fileToSend = new File(FILES_DIR + File.separator + requestedFileName); //สร้าง object fileToSend ของไฟล์ video ที่ฝั่ง client ต้องการ
                if (fileToSend.exists()) { //ตรวจสอบ path ส่าถูกต้องมีไฟล์ video ที่ client ต้องการ
                    dos.writeLong(fileToSend.length()); //เขียน(ส่ง)ขนาดของไฟล์ video ไปให้ client
                    // Zero-copy
                    if (transferMethod == 2) { //ตรวจสอบตัวแปร transferMethod ว่าต้องการส่งแบบไหน
                        try (FileInputStream fis = new FileInputStream(fileToSend);  //สร้าง object fis เพื่อชี้ไปยังไฟล์ที่ต้องการอ่าน
                             FileChannel fileChannel = fis.getChannel(); //สร้าง object fileChannel จากคลาส FileChannel เพื่อให้สามารถเข้าถึงข้อมูลในไฟล์ video ได้โดยตรง
                             WritableByteChannel writableChannel = Channels.newChannel(dos)) { //ใช้ Channels.newChannel(dos) เพื่อแปลง DataOutputStream (dos) ให้กลายเป็น WritableByteChannel ซึ่งสามารถใช้งานร่วมกับ transferTo ได้
                            // สร้าง WritableByteChannel (writableChannel) จาก DataOutputStream (dos) ซึ่งใช้สำหรับส่งข้อมูลออกไปในช่องทางที่สามารถเขียนข้อมูลได้ เช่น socket
                            long position = 0; //กำหนดตำแหน่งเริ่มต้นของข้อมูลที่จะถ่ายโอนใน FileChannel
                            long size = fileChannel.size(); //ตัวแปร size เก็บขนาดทั้งหมดของไฟล์
                            while (position < size) { //วนลูปจนกว่าจะถ่ายโอนข้อมูลครบ (ตำแหน่งแรกที่ถ่ายโอนยังน้อยกว่าขนาดไฟล์ที่เหลือ)
                                position += fileChannel.transferTo(position, size - position, writableChannel); //ทำการถ่ายโอนข้อมูลจาก FileChannel ไปยัง WritableByteChannel 
                                //fileChannel.transferTo(ตำแหน่งแรกที่เริ่มถ่ายโอน,จำนวนไบต์ที่เหลือ, channel ที่จะรับข้อมูล);
                                //การทำงานของ transferTo จะเพิ่ม position ด้วยจำนวน byte ที่ได้ถ่ายโอนในแต่ละครั้ง ซึ่งทำให้ position เพิ่มขึ้นเรื่อย ๆ จนกระทั่งเท่ากับ size
                                //transferTo มีประสิทธิภาพสูงกว่าเมื่อเทียบกับการอ่านข้อมูลด้วย buffer ทีละน้อยแล้วเขียนต่อไปยัง output เพราะมันสามารถถ่ายโอนข้อมูลในระดับ OS kernel ได้โดยตรง ซึ่งลดการใช้หน่วยความจำและทำให้ทำงานได้เร็วขึ้น
                            }
                        }
                    // Regular I/O copy
                    } else { 
                        try (FileInputStream fis = new FileInputStream(fileToSend)) { //สร้าง object fis เพื่อชี้ไปยังไฟล์ที่ต้องการอ่าน
                            byte[] buffer = new byte[8192]; //สร้างขนาด buffer ในการอ่านไฟล์
                            int read; //ตัวแปร read เก็บค่าที่อ่านในแต่จะรอบ
                            while ((read = fis.read(buffer)) != -1) { //ให้ object fis ที่สร้างจากคลาส FileInputStream เรียกใช้ method read() เพื่อทำการอ่านไฟล์ (อ่านเป็นไบต์) และเก็บไว้ในตัวแปร buffer     
                                dos.write(buffer, 0, read); //ให้ object dos เรียกใช้ method write เพื่อเขียน(ส่ง)ไฟล์ที่อ่านจาก buffer ไปให้ client
                            }
                        }
                    }
                    System.out.println("Sent file: " + requestedFileName + " to client.");
                } else {
                    dos.writeLong(0); // แจ้งให้ลูกค้าทราบว่าไม่พบไฟล์
                    System.out.println("Requested file not found: " + requestedFileName);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
