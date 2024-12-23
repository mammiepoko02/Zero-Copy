import java.io.*;
import java.net.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;



public class Client {
    static final String SERVER_ADDRESS = "localhost";
    static final int SERVER_PORT = 49230;
    static final String CLIENT_DIR = "C:\Users\smamm\OneDrive\Desktop\TC";

    public static void main(String[] args) {
        Socket socket = null;
        try {
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT); //สร้าง Socket รับส่งข้อมูล
            DataInputStream dis = new DataInputStream(socket.getInputStream()); //dis เป็น object ที่สร้างเพื่ออ่านข้อมูลจาก Server ที่ส่งมายัง client 
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream()); //dos เป็น object ที่สร้างเพื่อเขียนข้อมูลจาก client ไปสู่ server

            // Receive the list of files from the server
            int fileCount = dis.readInt(); //รับข้อมูลจำนวนไฟล์ที่มีอยู่ใน server เป็นจำนวนเต็ม
            String[] fileNames = new String[fileCount];
            for (int i = 0; i < fileCount; i++) {
                fileNames[i] = dis.readUTF(); ////รับข้อมูลชื่อไฟล์ที่มีอยู่ใน server เป็นเก็บลงในตัวแปรอาเรย์ fileNames
            }
            //แสดงชื่อไฟล์ที่มีใน Server
            System.out.println("Files available on the server:");
            for (String fileName : fileNames) {
                System.out.println(fileName);
            }
            //ให้ client พิมพ์ชื่อไฟล์ วิธีที่ต้องการรับส่งไฟล์
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            System.out.print("Enter the name of the file you want to download: ");
            String requestedFileName = br.readLine();
            System.out.print("Choose transfer method: 1 for Regular, 2 for Zero Copy: ");
            int transferMethod = Integer.parseInt(br.readLine());

            // Send the requested file name and transfer method to the server
            dos.writeUTF(requestedFileName); //ใช้ object dos ส่ง(เขียน)ชื่อไฟล์ที่ client ต้องการไปยัง Server
            dos.writeInt(transferMethod); //ใช้ object dos ส่ง(เขียน)ชื่อวิธีรับส่งไฟล์ที่ client ต้องการไปยัง Server

            // Receive the file from the server
            long fileSize = dis.readLong(); //รับขนาดไฟล์จาก Server เก็บลงตัวแปร fileSize
            if (fileSize > 0) {
                File clientDir = new File(CLIENT_DIR); //สร้าง object clientDir ของ didrectory ที่ใช้เก็บไฟล์ video ของฝั่ง client
                if (!clientDir.exists()) { //ตรวจสอบว่ามี directory มั้ย
                    clientDir.mkdirs(); //ไม่มีก็สร้างให้
                }
                File outputFile = new File(CLIENT_DIR + File.separator + requestedFileName); //สร้าง object outputFile ของไฟล์ video ที่ฝั่ง client ต้องการ

                // เริ่มจับเวลาที่ใช้ในการดาวน์โหลด
                long startTime = System.currentTimeMillis();

                if (transferMethod == 2) {
                    // Simulated Zero-Copy: using NIO Channels for improved performance
                    receiveFileWithChannel(socket, outputFile, fileSize); //เรียกใช้ method receiveFileWithChannel เพื่อรับไฟล์แบบ Zero Copy
                } else {
                    // Regular copy method
                    receiveFileRegular(dis, outputFile, fileSize); //เรียกใช้ method receiveFileWithChannel เพื่อรับไฟล์แบบปกติ
                }

                // หยุดการจับเวลาในการรับส่งไฟล์
                long endTime = System.currentTimeMillis();
                long elapsedTime = endTime - startTime; // in milliseconds

                System.out.printf("File downloaded successfully in %d milliseconds.\n", elapsedTime);
            } else {
                System.out.println("File not found on the server.");
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static void receiveFileRegular(DataInputStream dis, File outputFile, long fileSize) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(outputFile)) { //สร้างออบเจ็กต์ FileOutputStream (fos) สำหรับเขียนข้อมูลลงใน outputFile
            byte[] buffer = new byte[8192]; //สร้างบัฟเฟอร์ขนาด 8,192 ไบต์ (8 KB) เพื่อใช้เป็นตัวเก็บข้อมูลที่อ่านมาในแต่ละรอบของลูป
            int read; //ตัวแปร read เก็บค่าที่อ่านในแต่ละรอบ
            System.out.println("Downloading file...");
            while (fileSize > 0 && (read = dis.read(buffer, 0, (int)Math.min(buffer.length, fileSize))) != -1) { 
                //ลูปนี้จะทำงานจนกว่า fileSize จะเหลือศูนย์ (หมายถึงการถ่ายโอนข้อมูลครบถ้วนแล้ว) หรือเมื่ออ่านข้อมูลทั้งหมดแล้ว (dis.read(...) คืนค่า -1)
                //อ่านข้อมูลจาก DataInputStream (dis) ลงใน buffer
                //ใช้ Math.min(buffer.length, fileSize) เพื่อตรวจสอบให้แน่ใจว่าไม่อ่านเกินขนาดข้อมูลที่เหลืออยู่ (fileSize) ทำให้มั่นใจได้ว่าเมื่อไฟล์ใกล้จะเสร็จสิ้น ก็จะอ่านข้อมูลเท่าที่เหลืออยู่เท่านั้น
                fos.write(buffer, 0, read); //เขียนข้อมูลจาก buffer ลงใน FileOutputStream (fos) โดยเริ่มจากตำแหน่ง 0 จนถึงจำนวน byte ที่อ่าน (read).   
                fileSize -= read; //หักจำนวน byte ที่อ่านได้ (read) ออกจาก fileSize เพื่อลดจำนวน byte ที่เหลือสำหรับการอ่านข้อมูลในรอบถัดไป
            }
            System.out.println("Download complete.");
        }
    }

    private static void receiveFileWithChannel(Socket socket, File outputFile, long fileSize) throws IOException {
        try (ReadableByteChannel readableChannel = Channels.newChannel(socket.getInputStream()); // สร้าง ReadableByteChannel (readableChannel) จาก InputStream ของ socket เพื่อให้สามารถอ่านข้อมูลจาก socket ได้โดยใช้ FileChannel
             FileChannel fileChannel = new FileOutputStream(outputFile).getChannel()) { //สร้าง FileChannel (fileChannel) จาก FileOutputStream ของ outputFile เพื่อเขียนข้อมูลลงในไฟล์

            System.out.println("Downloading file using simulated zero-copy...");
            long totalRead = 0; //ตัวแปร totalRead ใช้เก็บจำนวน byte ที่อ่านได้ทั้งหมด (ดาวน์โหลดไปแล้ว) เพื่อเปรียบเทียบกับขนาดไฟล์ (fileSize)
            long position = 0; //ตำแหน่งใน fileChannel ที่จะเริ่มเขียนข้อมูลในแต่ละรอบ

            while (totalRead < fileSize) {//ลูปนี้จะทำงานจนกว่าจะถ่ายโอนข้อมูลครบ (totalRead ถึง fileSize).
                long transferred = fileChannel.transferFrom(readableChannel, position, fileSize - totalRead); //transferFrom เป็นเมธอดของ FileChannel ที่ช่วยให้สามารถถ่ายโอนข้อมูลจาก readableChannel ไปยัง fileChannel ได้โดยตรง
                //position ตำแหน่งใน fileChannel ที่จะเขียนข้อมูล (เริ่มต้นจาก 0)
                //fileSize - totalRead จำนวน byte ที่เหลือที่ต้องถ่ายโอน
                //transferred จำนวน byte ที่ถ่ายโอนได้จริงในรอบนี้
                if (transferred <= 0) break; //ถ้า transferred มีค่า 0 หรือน้อยกว่า แสดงว่าไม่มีข้อมูลให้อ่านเพิ่มเติม ทำให้ลูปสิ้นสุดทันที
                totalRead += transferred; //บวกจำนวน byte ที่ถ่ายโอนได้ในรอบนี้เข้ากับ totalRead
                position += transferred; //อัปเดตตำแหน่งเขียนใน fileChannel เพื่อเตรียมสำหรับการถ่ายโอนครั้งต่อไป
            }
            System.out.println("Download complete.");
        }
    }
}
