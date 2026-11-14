package ars.home.fileserver.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@RestController
public class Dashboard {

    private final String pathToFile;

    public Dashboard(@Value("${fileserver.file-path}") String pathToFile) {
        this.pathToFile = pathToFile;
    }


    @GetMapping(value = "/file/download",
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public @ResponseBody byte[] downLoadFile() throws IOException {
        return Files.readAllBytes(new File(pathToFile).toPath());
    }
}
