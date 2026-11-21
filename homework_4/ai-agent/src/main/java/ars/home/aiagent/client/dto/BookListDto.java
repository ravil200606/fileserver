package ars.home.aiagent.client.dto;

import java.util.List;

public record BookListDto(List<BookFileDto> files, long refreshIntervalSeconds) {
}
