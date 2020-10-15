import javax.smartcardio.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class Main {
    private static String pin2Path = "resource/est-pin2.secret";
    private static String pin2;
    // ==============================================================================
    // helper functions
    // ==============================================================================
    public static void readPin2(){
        try {
            File myObj = new File(pin2Path);
            Scanner myReader = new Scanner(myObj);
            while (myReader.hasNextLine()) {
                String data = myReader.nextLine();
                pin2 = data;
            }
            myReader.close();
        } catch (FileNotFoundException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }
    public static byte[] concat(byte[]... byteArrays) throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        for (byte[] byteArray : byteArrays) {
            stream.write(byteArray);
        }
        return stream.toByteArray();
    }

    private static byte[] padCode(byte[] code) {
        byte[] padded = Arrays.copyOf(code, 12);
        Arrays.fill(padded, code.length, padded.length, (byte) 0xFF);
        return padded;
    }

    //Helper function to pad hash with zeroes
    private static byte[] padWithZeroes(byte[] hash) throws IOException {
        if (hash.length >= 48) {
            return hash;
        }
        try (ByteArrayOutputStream toSign = new ByteArrayOutputStream()) {
            toSign.write(new byte[48 - hash.length]);
            toSign.write(hash);
            return toSign.toByteArray();
        }
    }

    public static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte aByte : bytes) {
            int decimal = (int) aByte & 0xff;               // bytes widen to int, need mask, prevent sign extension
            // get last 8 bits
            String hex = Integer.toHexString(decimal);
            if (hex.length() % 2 == 1) {                    // if half hex, pad with zero, e.g \t
                hex = "0" + hex;
            }
            result.append(hex);
        }
        return result.toString();
    }
    // ==============================================================================

    public static void getCardNumber(CardChannel channel) {
        try {
            //Select master file
            CommandAPDU selectMF = new CommandAPDU(0x00, 0xA4, 0x00, 0x0C);
            channel.transmit(selectMF);

            //Select document number EF
            CommandAPDU selectDocumentNumberEF = new CommandAPDU(0x00, 0xA4, 0x02, 0x0C, new byte[]{(byte) 0xD0, 0x03});
            channel.transmit(selectDocumentNumberEF);

            //Read binary and convert the response data into an UTF-8 string
            CommandAPDU readBinary = new CommandAPDU(new byte[]{0x00, (byte) 0xB0, 0x00, 0x00, 0x00});
            ResponseAPDU response = channel.transmit(readBinary);
            String documentNumber = new String(response.getData(), StandardCharsets.UTF_8).trim();

            System.out.println("document number:" + documentNumber);
        } catch (Exception error) {
            System.out.println("error getCardNumber:" + error);
        }
    }

    public static void getPersonInformation(CardChannel channel) {
        String[] infoName = {"Surname", "Firstname", "Sex", "Citizenship", "Date and place of birth", "Personal identification code", "Document number", "Expiry date", "Date of issuance", "Type of residence", "Notes line 1", "Notes line 2", "Notes line 3", "Notes line 4", "Notes line 5"};
        try {
            //Select master file
            CommandAPDU selectMF = new CommandAPDU(0x00, 0xA4, 0x00, 0x0C);
            channel.transmit(selectMF);

            //Select personal data DF
            CommandAPDU selectPersonalDataFile = new CommandAPDU(0x00, 0xA4, 0x01, 0x0C, new byte[]{0x50, 0x00});
            channel.transmit(selectPersonalDataFile);

            //Select first name record
            CommandAPDU selectFirstNameRecord = new CommandAPDU(0x00, 0xA4, 0x02, 0x0C, new byte[]{0x50, 0x02});
            channel.transmit(selectFirstNameRecord);

            CommandAPDU readBinary = new CommandAPDU(new byte[]{0x00, (byte)0xB0, 0x00,0x00, 0x00});
            for (int i = 1; i <= 15; i++) {
                CommandAPDU selectChildEF = new CommandAPDU(0x00, 0xA4, 0x02, 0x0C, new
                        byte[] {0x50, (byte) i});
                channel.transmit(selectChildEF);
                ResponseAPDU response = channel.transmit(readBinary);
                String record = new String(response.getData(), StandardCharsets.UTF_8).trim();
                System.out.println(infoName[i-1] + ": " + record);
            }
        } catch (Exception error) {
            System.out.println("error getPersonInformation:" + error);
        }
    }

    public static void getPin2RemainingTrials(CardChannel channel) {
        try {
            //Select master file
            CommandAPDU selectMF = new CommandAPDU(0x00, 0xA4, 0x00, 0x0C);
            channel.transmit(selectMF);

            //Select QSCD ADF
            byte[] qscdFid = new byte[]{(byte) 0xAD, (byte) 0xF2};
            CommandAPDU selectQSCDAdf = new CommandAPDU(0x00, 0xA4, 0x01, 0x0C, qscdFid);
            channel.transmit(selectQSCDAdf);
//Verify PIN2
            CommandAPDU verifyPin2 = new CommandAPDU(0x00, 0x20, 0x00, 0x85);
            ResponseAPDU response = channel.transmit(verifyPin2);
//Get retry count from SW
            String sw = Integer.toHexString(response.getSW());
            int retryCount = Integer.parseInt(sw.substring(sw.length() - 1));


            System.out.println("pin2 remaining trials:" + retryCount);
        } catch (Exception error) {
            System.out.println("error getPin2RemainingTrials:" + error);
        }
    }

    public static void signMessage(CardChannel channel) {
        try {
            //Select master file
            CommandAPDU selectMF = new CommandAPDU(0x00, 0xA4, 0x00, 0x0C);
            channel.transmit(selectMF);

            //Select QSCD application
            byte[] qscdFid = new byte[]{(byte) 0xAD, (byte) 0xF2};
            CommandAPDU selectQSCDAdf = new CommandAPDU(0x00, 0xA4, 0x01, 0x0C, qscdFid);
            channel.transmit(selectQSCDAdf);

            //Verify PIN2
            CommandAPDU verifyPin2 = new CommandAPDU(0x00, 0x20, 0x00, 0x85, padCode(pin2.getBytes()));
            ResponseAPDU response = channel.transmit(verifyPin2);

            //Proceed with signature calculation if pin2 verification was successful
            if ("9000".equalsIgnoreCase(Integer.toHexString(response.getSW()))) {
                //Set Security environment
                CommandAPDU setEnvironment = new CommandAPDU(0x00, 0x22, 0x41, 0xB6, new
                        byte[]{(byte) 0x80, 0x04, (byte) 0xFF, 0x15, 0x08, 0x00, (byte) 0x84, 0x01, (byte) 0x9F});
                channel.transmit(setEnvironment); //SHA-256 hash
                String text = "JÕEORG";
                byte[] sha256DigestValue = MessageDigest.getInstance("SHA-256").digest(text.getBytes());

                //pad with zeroes
                byte[] padded = padWithZeroes(sha256DigestValue);

                //calculate digital signature
                CommandAPDU securityOperationComputeSignature =
                        new CommandAPDU(concat(new byte[]{0x00, 0x2A, (byte) 0x9E, (byte) 0x9A, (byte) padded.length}, padded, new byte[]{0x00}));
                byte[] signature = channel.transmit(securityOperationComputeSignature).getData();

                String signatureStr = new String(signature, StandardCharsets.UTF_8).trim();
                System.out.println("signature:" + hex(signature));
            } else {
                System.out.println("error signMessage card response:" + Integer.toHexString(response.getSW()));
            }
        } catch (Exception error) {
            System.out.println("error signMessage:" + error);
        }
    }

    public static void main(String[] args) {
        readPin2();

        try {
            //Get a list of available card terminals
            List<CardTerminal> terminals = TerminalFactory.getDefault().terminals().list();
            //You can select the correct terminal by filtering the list by name or whether it has a card present
            //For the purposes of this example let's just choose the first one from the list
            CardTerminal terminal = terminals.get(0);
            //Connect with card (using the T=1 protocol)
            Card card = terminal.connect("T=1");

            //Establish a channel
            CardChannel channel = card.getBasicChannel();

            getCardNumber(channel);
            getPersonInformation(channel);
            signMessage(channel);
            getPin2RemainingTrials(channel);
        } catch (Exception error) {
            System.out.println("error main:" + error);
        }

    }
}