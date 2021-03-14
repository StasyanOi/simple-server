import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainServer {

    static ExecutorService executorService = Executors.newFixedThreadPool(3);

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(8081)) {
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
        } catch (IOException e) {
            e.printStackTrace();
            executorService.shutdownNow();
        }
    }

    private static void process(Socket accept) throws IOException {

        StringBuilder request = new StringBuilder();
        try (InputStreamReader inputStreamReader = new InputStreamReader(accept.getInputStream())) {
            while (true) {
                int read = -10;
                if (inputStreamReader.ready()) {
                    read = inputStreamReader.read();
                } else {
                    break;
                }
                if (read == -1) {
                    break;
                } else if (read == 10) {
                    request.append((char) 10);
                } else {
                    request.append((char) read);
                }
            }

            HttpRequest httpRequest = getRequest(request);
            HttpResponse httpResponse = getResponse(200, "Hello world");
            System.out.println(httpRequest.getRequestLine());
            System.out.println(httpRequest.getHeaders());
            System.out.println(httpRequest.getBody());

            try (OutputStreamWriter outputStreamWriter = new OutputStreamWriter(accept.getOutputStream())) {
                outputStreamWriter.write(httpResponse.responseString());
                outputStreamWriter.flush();
                inputStreamReader.close();
                accept.close();
            }
        }

    }

    private static HttpResponse getResponse(int statusCode, String body) {
        return new HttpResponse(statusCode, body);
    }

    private static HttpRequest getRequest(StringBuilder request) {
        List<String> list = Arrays.asList(request.toString().split("\n"));
        return new HttpRequest(list);
    }

    private static class HttpResponse {
        private final String responseLine;
        private final Map<String, String> headers = new HashMap<>();
        private final String body;
        private final String crlf = "\r\n";

        public HttpResponse(int status, String body) {
            responseLine = "HTTP/1.1 " + status;
            headers.put("Content-Type: ", "text/plain");
            headers.put("Content-Length: ", "" + body.length());
            this.body = body;
        }

        public String responseString() {
            StringBuilder response = new StringBuilder(responseLine + crlf);
            headers.forEach((key, val) -> response.append(key).append(": ").append(val).append(crlf));
            response.append(crlf);
            response.append(body);
            return response.toString();
        }
    }

    private static class HttpRequest {
        private final String requestLine;
        private final Map<String, String> headers = new HashMap<>();
        private final String body;

        public HttpRequest(List<String> request) {
            requestLine = request.get(0);

            int i = 1;
            String header;
            while (!(header = request.get(i++)).equals("\r")) {
                String[] splitHeader = header.split(":");
                headers.put(splitHeader[0], splitHeader[1].substring(1, splitHeader[1].length() - 1));
            }

            StringBuilder body = new StringBuilder();
            for (int j = i++; j < request.size(); j++) {
                body.append(request.get(j));
            }
            this.body = body.toString();
        }

        public String getHeader(String headerName) {
            return headers.get(headerName);
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public String getBody() {
            return body;
        }

        public String getRequestLine() {
            return requestLine;
        }

        @Override
        public String toString() {
            return "HttpRequest{" +
                    "requestLine='" + requestLine + '\n' +
                    ", headers=" + headers +
                    ", body='" + body + '\n' +
                    '}';
        }
    }
}
