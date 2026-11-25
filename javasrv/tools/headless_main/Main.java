import com.hlw.srv.*;
import java.io.File;
public class Main {
    public static void main(String[] a) throws Exception {
        Store.init(new File(System.getProperty("hlw.dir", "/tmp/hlwdata")), null);
        Http.start(); Tcp.start();
        System.out.println("READY");
        Thread.currentThread().join();
    }
}
