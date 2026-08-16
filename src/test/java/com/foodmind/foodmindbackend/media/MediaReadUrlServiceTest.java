package com.foodmind.foodmindbackend.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.foodmind.foodmindbackend.media.application.MediaReadUrlService;
import com.foodmind.foodmindbackend.media.application.port.MediaAssetRepository;
import com.foodmind.foodmindbackend.media.application.port.MediaReadUrlPort;
import com.foodmind.foodmindbackend.media.domain.model.MediaAsset;
import com.foodmind.foodmindbackend.media.domain.model.MediaAssetStatus;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class MediaReadUrlServiceTest {

    @Mock
    private MediaAssetRepository repository;

    @Mock
    private ObjectProvider<MediaReadUrlPort> provider;

    @Mock
    private MediaReadUrlPort readUrlPort;

    @Test
    void signsOnlyReadyAssetsAfterTheParentResourceWasAuthorised() {
        UUID assetId = UUID.randomUUID();
        String objectKey = "media/owner/asset/original";
        MediaAsset ready = new MediaAsset(
                assetId,
                UUID.randomUUID(),
                objectKey,
                "image/png",
                128,
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                MediaAssetStatus.READY,
                OffsetDateTime.now().minusMinutes(1),
                OffsetDateTime.now(),
                null);
        when(repository.findReady(assetId)).thenReturn(Optional.of(ready));
        when(provider.getIfAvailable()).thenReturn(readUrlPort);
        when(readUrlPort.createReadUrl(objectKey)).thenReturn("https://storage.example/read");

        String result = new MediaReadUrlService(repository, provider).forAuthorisedAsset(assetId);

        assertThat(result).isEqualTo("https://storage.example/read");
    }

    @Test
    void pendingOrDeletedAssetsDoNotReachTheStorageSigner() {
        UUID assetId = UUID.randomUUID();
        when(repository.findReady(assetId)).thenReturn(Optional.empty());

        String result = new MediaReadUrlService(repository, provider).forAuthorisedAsset(assetId);

        assertThat(result).isNull();
        verifyNoInteractions(provider, readUrlPort);
    }

    @Test
    void unavailableStorageDegradesToAnAbsentImage() {
        when(provider.getIfAvailable()).thenReturn(readUrlPort);
        when(readUrlPort.createReadUrl("media/object")).thenThrow(new IllegalStateException("storage unavailable"));

        String result = new MediaReadUrlService(repository, provider).forAuthorisedObjectKey("media/object");

        assertThat(result).isNull();
    }
}
