import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MainServer {

    private static String fileFolder;

    static ExecutorService executorService = Executors.newFixedThreadPool(10);
    static Logger log = Logger.getLogger(MainServer.class.getName());

    public static void main(String[] args) {
        fileFolder = args[0];
        log.info("Starting server");
        int port = 8081;
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            log.info("Server starter at port " + port);
            while (true) {
                Socket accept = serverSocket.accept();
                executorService.submit(() -> {
                    try {
                        process(accept);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            }
        } catch (IOException | RejectedExecutionException e) {
            e.printStackTrace();
        }
        executorService.shutdown();
    }


    private static void process(Socket accept) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(accept.getInputStream()));
        BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(accept.getOutputStream()));

        requestMatchers(accept, bufferedReader, bufferedWriter);
    }

    private static void requestMatchers(Socket accept, BufferedReader bufferedReader, BufferedWriter bufferedWriter) throws IOException {
//        showRequest(accept);
        List<Character> ints = new ArrayList<>();
        while (bufferedReader.ready()) {
            ints.add((char) bufferedReader.read());
        }
        String collect = ints.stream().map(Object::toString).collect(Collectors.joining());
        Scanner scanner = new Scanner(collect);
        List<String> requestLines = new ArrayList<>();
        while (scanner.hasNext()) {
            requestLines.add(scanner.nextLine());
        }
        if (requestLines.size() == 0) {
            accept.close();
        }
        if (getHomePage(requestLines)) {
            requestLines.forEach(System.out::println);
            Path path = Paths.get("Hello.html");
            List<String> html = Files.lines(path).collect(Collectors.toList());
            for (int i = 0; i < html.size(); i++) {
                if (html.get(i).contains("<body>")) {
                    addFiles(html, i);
                    break;
                }
            }
            bufferedWriter.write(new HttpResponse(200, String.join("\n", html), MimeType.html.getContentType()).responseString());
            bufferedWriter.flush();
        } else if (getFile(requestLines)) {
            String fileName = requestLines.get(0).split(" ")[1];
            requestLines.forEach(System.out::println);
            Path path = Paths.get(fileName);
            if (Files.exists(path)) {
                BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(accept.getOutputStream());
                byte[] bytes = Files.readAllBytes(path);
                byte[] header = HttpResponse.responseHeader(bytes, 200, MimeType.undefined.getContentType()).getBytes(StandardCharsets.UTF_8);
                byte[] total = concatArrays(header, bytes);
                bufferedOutputStream.write(total);
                bufferedOutputStream.flush();
            }
        } else if (fileForSaving(requestLines)) {
            String req = collect;
            System.out.println(req);
            Pattern pattern = Pattern.compile("boundary=.+\r\n");
            Matcher matcher = pattern.matcher(req);
//            boundary=----WebKitFormBoundary7W9ZBDRZUVVFxprE
//            boundary=----WebKitFormBoundaryPAxf1C6lJMCelFYw
//            boundary=----WebKitFormBoundarypaO4tPMoDpRDJ86U

            if (matcher.find()) {
                String keyValue = matcher.group();
                String delimit = keyValue.split("=")[1];
                String delimiter = "--" + delimit;
                System.out.println(delimiter);
                String[] split = collect.split(delimiter);
                System.out.println(split);
                String request = split[0];
                String file = split[1];
                Pattern pattern1 = Pattern.compile("\r\n\r\n");
                Matcher matcher1 = pattern1.matcher(file);
                boolean b = matcher1.find();
                int fileStart = matcher1.start() + 4;
                byte[] bytes = file.substring(fileStart).getBytes();
                FileOutputStream fileOutputStream = new FileOutputStream("test.png");
                fileOutputStream.write(bytes);
                fileOutputStream.flush();



                String submit = split[2];
            }
            String[] split = req.split("------WebKitFormBoundaryHshHAD5NK286MxVu");

        }
        accept.close();
    }

    private static void showRequest(Socket accept) {
        try {
            DataInputStream dataInputStream = new DataInputStream(new BufferedInputStream(accept.getInputStream()));
            System.out.println(dataInputStream.readUTF());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static boolean fileForSaving(List<String> requestLines) {
        return requestLines.get(0).contains("POST");
    }

    private static void addFiles(List<String> html, int i) throws IOException {
        Path path = Paths.get(fileFolder);
        Files.list(path).forEach(s -> html.add(i, getFileLine(s)));
    }

    private static String getFileLine(Path s) {
        String substring = s.toString();
        String[] split = substring.split("/");
        return "<a href=\"" + substring + "\">" + split[split.length - 1] + "<a>\n";
    }

    private static byte[] concatArrays(byte[] header, byte[] bytes) {
        byte[] total = new byte[header.length + bytes.length];
        System.arraycopy(header, 0, total, 0, header.length);
        System.arraycopy(bytes, 0, total, header.length, bytes.length);
        return total;
    }

    private static boolean getFile(List<String> requestLines) {
        if (requestLines.size() != 0) {
            String[] split = requestLines.get(0).split(" ");
            Pattern pattern = Pattern.compile("/+.");
            Matcher matcher = pattern.matcher(split[1]);
            return split[0].equals("GET") && matcher.find();
        }
        return false;
    }

    private static boolean getHomePage(List<String> requestLines) {
        if (requestLines.size() != 0) {
            String[] split = requestLines.get(0).split(" ");
            return split[0].equals("GET") && split[1].equals("/");
        }
        return false;
    }

    private static List<String> getRequest(BufferedReader bufferedReader) throws IOException {
        List<String> requestLines = new ArrayList<>();
        while (bufferedReader.ready()) {
            String readLine = bufferedReader.readLine();
            requestLines.add(readLine);
        }
        return requestLines;
    }


    private static class HttpResponse {
        private final String responseLine;
        private final Map<String, String> headers = new HashMap<>();
        private final String body;
        private static final String crlf = "\r\n";

        public HttpResponse(int status, String body, String contentType) {
            responseLine = "HTTP/1.1 " + status;
            headers.put("Content-Type", contentType);
            headers.put("Content-Length", "" + body.length());
            this.body = body;
        }

        public String responseString() {
            StringBuilder response = new StringBuilder(responseLine + crlf);
            headers.forEach((key, val) -> response.append(key).append(": ").append(val).append(crlf));
            response.append(crlf);
            response.append(body);
            return response.toString();
        }

        public static String responseHeader(byte[] body, int status, String contentType) {
            StringBuilder response = new StringBuilder("HTTP/1.1 " + status + crlf);
            response.append("Content-Type").append(": ").append(contentType).append(crlf);
            response.append("Content-Length").append(": ").append(body.length).append(crlf);
            response.append(crlf);
            return response.toString();
        }
    }
}
