package com.familyalbum.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.familyalbum.config.AppProperties;
import com.familyalbum.domain.PhotoContent;
import com.familyalbum.repository.PhotoContentRepository;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;

class FileStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void storesAndLoadsPhotoBytesThroughRepository() throws Exception {
        PhotoContentRepository repository = org.mockito.Mockito.mock(PhotoContentRepository.class);
        FileStorage storage = new FileStorage(
                new AppProperties(new AppProperties.Storage(tempDir.toString()), null, null, null, null),
                repository);
        byte[] expected = new byte[] {1, 2, 3, 4};

        String storedName = storage.store(42L, () -> new ByteArrayInputStream(expected), ".jpg");

        ArgumentCaptor<PhotoContent> captor = ArgumentCaptor.forClass(PhotoContent.class);
        verify(repository).save(captor.capture());
        assertEquals(42L, captor.getValue().getFamilyId());
        assertEquals(storedName, captor.getValue().getStoredFilename());
        assertArrayEquals(expected, captor.getValue().getContent());

        when(repository.findByFamilyIdAndStoredFilename(42L, storedName))
                .thenReturn(Optional.of(captor.getValue()));
        ByteArrayResource resource = (ByteArrayResource) storage.load(42L, storedName);
        assertArrayEquals(expected, resource.getByteArray());
    }

    @Test
    void deletesDatabaseContentByFamilyAndFilename() {
        PhotoContentRepository repository = org.mockito.Mockito.mock(PhotoContentRepository.class);
        FileStorage storage = new FileStorage(
                new AppProperties(new AppProperties.Storage(tempDir.toString()), null, null, null, null),
                repository);

        storage.delete(42L, "sample.jpg");

        verify(repository).deleteByFamilyIdAndStoredFilename(42L, "sample.jpg");
    }
}
