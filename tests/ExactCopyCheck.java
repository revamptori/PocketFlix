package app.pocketflix;
import java.io.*;
import java.util.Arrays;
public class ExactCopyCheck {
  public static void main(String[] args)throws Exception {
    byte[] sample=new byte[150001];new java.util.Random(1).nextBytes(sample);
    ExactCopy.Progress p=new ExactCopy.Progress(){public void bytes(long n){}};
    ByteArrayOutputStream out=new ByteArrayOutputStream();ExactCopy.copy(new ByteArrayInputStream(sample),out,sample.length,p);
    if(!Arrays.equals(sample,out.toByteArray()))throw new AssertionError();
    for(long size:new long[]{1,sample.length+1})try{ExactCopy.copy(new ByteArrayInputStream(sample),out,size,p);throw new AssertionError();}catch(IOException ok){}
    System.out.println("PASS: exact, oversized and truncated HTTPS bodies");
  }
}
