package com.server;

import com.MainServer;
import com.util.MimeType;

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

public class MyServer implements Server {

    private String fileFolder;
    static ExecutorService executorService = Executors.newFixedThreadPool(10);
    static Logger log = Logger.getLogger(MainServer.class.getName());

    public void run(String[] args) {
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

    private void process(Socket accept) throws IOException {
        BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(accept.getOutputStream()));

        requestMatchers(accept, bufferedWriter);
    }

    private void requestMatchers(Socket accept, BufferedWriter bufferedWriter) throws IOException {

        BufferedInputStream bufferedInputStream = new BufferedInputStream(accept.getInputStream());
        List<Integer> ints = new ArrayList<>();
        while (bufferedInputStream.available() != 0) {
            int read = bufferedInputStream.read();
            ints.add(read);
        }
        String collect = ints.stream().map(integer -> (char) integer.intValue())
                .map(Object::toString).collect(Collectors.joining());
        Scanner scanner = new Scanner(collect);
        List<String> requestLines = new ArrayList<>();
        while (scanner.hasNext()) {
            requestLines.add(scanner.nextLine());
        }
        if (requestLines.size() == 0) {
            accept.close();
        }
        if (getHomePage(requestLines)) {
            loadHomePage(bufferedWriter, requestLines);
        } else if (getFile(requestLines)) {
            if (isFavicon(requestLines.get(0))) {
                loadFavicon(accept);
            } else {
                loadFile(accept, requestLines);
            }
            loadHomePage(bufferedWriter, requestLines);
        } else if (fileForSaving(requestLines)) {
            saveFile(collect);
            loadHomePage(bufferedWriter, requestLines);
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
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(accept.getOutputStream());
            byte[] bytes = Files.readAllBytes(path);
            byte[] header = HttpResponse.responseHeader(bytes, 200, MimeType.undefined.getContentType()).getBytes(StandardCharsets.UTF_8);
            byte[] total = concatArrays(header, bytes);
            bufferedOutputStream.write(total);
            bufferedOutputStream.flush();
        }
    }

    private boolean isFavicon(String firstRequestLine) {
        return firstRequestLine.contains("favicon.ico");
    }

    private void loadHomePage(BufferedWriter bufferedWriter, List<String> requestLines) throws IOException {
        requestLines.forEach(System.out::println);
        Path path = Paths.get("Hello.html");
        List<String> html = Files.lines(path).collect(Collectors.toList());
        for (int i = 0; i < html.size(); i++) {
            if (html.get(i).contains("<body>")) {
                addFiles(html, i);
                break;
            }
        }
        String responseString = new HttpResponse(200, String.join("\n", html), MimeType.html.getContentType()).responseString();
        bufferedWriter.write(responseString);
        bufferedWriter.flush();
    }

    private void loadFile(Socket accept, List<String> requestLines) throws IOException {
        String fileName = requestLines.get(0).split(" ")[1].replace("%20", " ");
        writeFileToSocket(accept, fileName);
    }

    private void saveFile(String collect) throws IOException {
        Pattern pattern = Pattern.compile("boundary=.+\r\n");
        Matcher matcher = pattern.matcher(collect);
        if (matcher.find()) {
            String keyValueBoundary = matcher.group();
            String delimiter = keyValueBoundary.split("=")[1];
            delimiter = "--" + delimiter;
            String[] requestParts = collect.split(delimiter);
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

    private byte[] concatArrays(byte[] header, byte[] bytes) {
        byte[] total = new byte[header.length + bytes.length];
        System.arraycopy(header, 0, total, 0, header.length);
        System.arraycopy(bytes, 0, total, header.length, bytes.length);
        return total;
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
            return "HTTP/1.1 " + status + crlf + "Content-Type" + ": " + contentType + crlf +
                    "Content-Length" + ": " + body.length + crlf +
                    crlf;
        }
    }
}
