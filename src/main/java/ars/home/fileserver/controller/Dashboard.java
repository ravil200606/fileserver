package ars.home.fileserver.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@RestController
@RequiredArgsConstructor
public class Dashboard {


    private static final String PATH_TO_FLE = "/home/ravil/files/mif.zip";


    @GetMapping(value = "/file/download",
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public @ResponseBody byte[] downLoadFile() throws IOException {
        return Files.readAllBytes(new File(PATH_TO_FLE).toPath());
    }
}
