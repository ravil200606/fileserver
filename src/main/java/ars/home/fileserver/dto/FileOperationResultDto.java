package ars.home.fileserver.dto;

/**
 * Результат операции над выбранными файлами (архивирование, восстановление, удаление).
 *
 * @param succeeded количество успешно обработанных файлов
 * @param failed    количество файлов, которые не удалось обработать
 */
public record FileOperationResultDto(int succeeded, int failed) {
}
