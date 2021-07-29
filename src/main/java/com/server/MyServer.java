package com.server;

import com.MainServer;
import com.util.HttpHeaders;
import com.util.MimeType;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.util.Arrays.concatArrays;
import static com.util.Delimiters.crlf;

public class MyServer implements Server {

    private final String fileFolder;
    private final Integer port;
    private boolean shouldStop = false;
    static ExecutorService executorService = Executors.newFixedThreadPool(10);
    static Logger log = Logger.getLogger(MainServer.class.getName());

    public MyServer(String[] args) {
        if (args.length == 2) {
            fileFolder = args[0];
            port = Integer.parseInt(args[1]);
        } else {
            throw new IllegalArgumentException("Illegal arguments " + Arrays.toString(args));
        }
    }

    @Override
    public void run() throws IOException {
        startListener();
    }

    @Override
    public void stop() throws IOException {
        stopListener();
    }

    private void stopListener() throws IOException {
        shouldStop = true;
        Socket client = new Socket();
        SocketAddress socketAddress = new InetSocketAddress(port);
        client.connect(socketAddress);
        log.info("Stopping server");
    }

    private void startListener() throws IOException {
        log.info("Starting server");
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            log.info("Server starter at port " + port);
            while (true) {
                Socket accept = serverSocket.accept();
                if (shouldStop) {
                    accept.close();
                    break;
                }
                executorService.submit(() -> {
                    try {
                        process(accept);
                    } catch (Exception exception) {
                        log.info(exception.toString());
                        write500Error(accept, exception);
                    }
                });
            }
        } finally {
            executorService.shutdown();
        }
    }

    private void write500Error(Socket accept, Exception exception) {
        HttpResponse httpResponse = HttpResponse.create(500, exception.toString().getBytes(StandardCharsets.UTF_8),
                MimeType.text);
        try {
            writeToSocket(accept, httpResponse.getBytes());
            accept.close();
        } catch (Exception ex) {
            log.info("SOCKET EXCEPTION");
            ex.printStackTrace();
        }
    }

    private void process(Socket accept) throws IOException {
        requestMatchers(accept);
    }

    private void requestMatchers(Socket accept) throws IOException {
        BufferedInputStream bufferedInputStream = new BufferedInputStream(accept.getInputStream());
        BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(accept.getOutputStream());
        if (bufferedInputStream.available() == 0) {
            accept.close();
            return;
        }
        List<Integer> ints = new ArrayList<>();
        while (bufferedInputStream.available() != 0) {
            int read = bufferedInputStream.read();
            ints.add(read);
        }
        String request = ints.stream()
                .map(integer -> (char) integer.intValue())
                .map(Object::toString)
                .collect(Collectors.joining());
        Scanner scanner = new Scanner(request);
        List<String> requestLines = new ArrayList<>();
        while (scanner.hasNext()) {
            requestLines.add(scanner.nextLine());
        }

        if (getHomePage(requestLines)) {
            loadHomePage(bufferedOutputStream);
        } else if (getFile(requestLines)) {
            if (isFavicon(requestLines)) {
                loadFavicon(accept);
            } else {
                loadFile(accept, requestLines);
            }
            loadHomePage(bufferedOutputStream);
        } else if (fileForSaving(requestLines)) {
            saveFile(request);
            loadHomePage(bufferedOutputStream);
        }

        accept.close();
    }

    private void loadFavicon(Socket accept) throws IOException {
        String fileName = "./favicon.ico";
        writeFileToSocket(accept, fileName);
    }

    private void writeFileToSocket(Socket accept, String fileName) throws IOException {
        Path path = Paths.get(fileName);
        if (Files.exists(path)) {
            byte[] fileBytes = Files.readAllBytes(path);
            HttpResponse httpResponse = HttpResponse.create(200, fileBytes, MimeType.undefined);
            writeToSocket(accept, httpResponse.getBytes());
        }
    }

    private void writeToSocket(Socket accept, byte[] total) throws IOException {
        BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(accept.getOutputStream());
        bufferedOutputStream.write(total);
        bufferedOutputStream.flush();
    }

    private boolean isFavicon(List<String> requestLines) {
        return requestLines.get(0).contains("favicon.ico");
    }

    private void loadHomePage(BufferedOutputStream bufferedOutputStream) throws IOException {
        Path path = Paths.get("Hello.html");
        List<String> html = Files.lines(path).collect(Collectors.toList());
        for (int i = 0; i < html.size(); i++) {
            if (html.get(i).contains("<body>")) {
                addFiles(html, i);
                break;
            }
        }
        HttpResponse httpResponse = HttpResponse.create(200, String.join("\n", html).getBytes(StandardCharsets.UTF_8),
                MimeType.html);
        bufferedOutputStream.write(httpResponse.getBytes());
        bufferedOutputStream.flush();
    }

    private void loadFile(Socket accept, List<String> requestLines) throws IOException {
        String fileName = requestLines.get(0).split(" ")[1].replace("%20", " ");
        writeFileToSocket(accept, fileName);
    }

    private void saveFile(String request) throws IOException {
        Pattern boundaryPattern = Pattern.compile("boundary=.+\r\n");
        Matcher boundaryMatcher = boundaryPattern.matcher(request);
        if (boundaryMatcher.find()) {
            String keyValueBoundary = boundaryMatcher.group();
            String delimiter = keyValueBoundary.split("=")[1];
            delimiter = "--" + delimiter;
            String[] requestParts = request.split(delimiter);
            String file = requestParts[1];
            Pattern fileInfoBoundary = Pattern.compile("\r\n\r\n");
            Matcher fileBoundaryMatcher = fileInfoBoundary.matcher(file);
            if (fileBoundaryMatcher.find()) {
                int fileBoundaryStart = fileBoundaryMatcher.start();
                Pattern filenameInfo = Pattern.compile("filename=.+\r\n");
                Matcher filenameMatcher = filenameInfo.matcher(file);
                if (filenameMatcher.find()) {
                    String keyValueFilename = filenameMatcher.group();
                    String filename = keyValueFilename.split("=")[1].replace("\"", "");
                    filename = filename.substring(0, filename.length() - 2);
                    int fileStart = fileBoundaryStart + 4;
                    String pureFile = file.substring(fileStart, file.length() - 2);
                    int[] fileInts = pureFile.chars().toArray();
                    Path path = Paths.get(fileFolder, filename);
                    FileOutputStream fileOutputStream = new FileOutputStream(path.toString());
                    for (int fileInt : fileInts) {
                        fileOutputStream.write(fileInt);
                    }
                    fileOutputStream.flush();
                }
            }
        }
    }

    private boolean fileForSaving(List<String> requestLines) {
        return requestLines.get(0).contains("POST");
    }

    private void addFiles(List<String> html, int i) throws IOException {
        Path path = Paths.get(fileFolder);
        Files.list(path).forEach(s -> html.add(i, getFileLine(s)));
    }

    private String getFileLine(Path s) {
        String substring = s.toString();
        String[] split = substring.split("/");
        return "<a href=\"" + substring + "\">" + split[split.length - 1] + "<a>\n";
    }

    private boolean getFile(List<String> requestLines) {
        if (requestLines.size() != 0) {
            String[] split = requestLines.get(0).split(" ");
            Pattern pattern = Pattern.compile("/+.");
            Matcher matcher = pattern.matcher(split[1]);
            return split[0].equals("GET") && matcher.find();
        }
        return false;
    }

    private boolean getHomePage(List<String> requestLines) {
        if (requestLines.size() != 0) {
            String[] split = requestLines.get(0).split(" ");
            return split[0].equals("GET") && split[1].equals("/");
        }
        return false;
    }

    private static class HttpResponse {

        private final int status;
        private final MimeType contentType;
        private final byte[] body;
        private final HashMap<String, String> headers;

        public static HttpResponse create(int status, byte[] body, MimeType contentType) {
            return new HttpResponse(status, body, contentType);
        }

        private HttpResponse(int status, byte[] body, MimeType contentType) {
            this.status = status;
            this.contentType = contentType;
            this.body = body;
            this.headers = new HashMap<>();
            addHeaders();
        }

        private void addHeaders() {
            headers.put(HttpHeaders.CONTENT_TYPE, contentType.getContentType());
            headers.put(HttpHeaders.CONTENT_LENGTH, String.valueOf(body.length));
        }

        private byte[] getBytes() {
            String headers = this.headers.entrySet().stream()
                    .map(header -> header.getKey() + ": " + header.getValue() + crlf)
                    .collect(Collectors.joining());
            String requestHeader = "HTTP/1.1 " + status + crlf
                    + headers
                    + crlf;
            return concatArrays(requestHeader.getBytes(StandardCharsets.UTF_8), body);
        }
    }
}
