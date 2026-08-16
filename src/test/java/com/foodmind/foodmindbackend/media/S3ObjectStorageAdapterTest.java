package com.foodmind.foodmindbackend.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.foodmind.foodmindbackend.media.application.port.ObjectStoragePort.ObjectMetadata;
import com.foodmind.foodmindbackend.media.application.port.ObjectStoragePort.UploadInstruction;
import com.foodmind.foodmindbackend.media.infrastructure.storage.S3ObjectStorageAdapter;
import com.foodmind.foodmindbackend.media.infrastructure.storage.S3StorageProperties;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ChecksumMode;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@ExtendWith(MockitoExtension.class)
class S3ObjectStorageAdapterTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner presigner;

    private S3StorageProperties properties;
    private S3ObjectStorageAdapter adapter;

    @BeforeEach
    void setUp() {
        properties = new S3StorageProperties();
        properties.setBucket("private-media");
        properties.setUploadTtl(Duration.ofMinutes(5));
        properties.setReadTtl(Duration.ofMinutes(15));
        adapter = new S3ObjectStorageAdapter(s3Client, presigner, properties);
    }

    @Test
    void headObjectExplicitlyRequestsStoredChecksums() {
        String checksumHex = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        String checksumBase64 = Base64.getEncoder().encodeToString(HexFormat.of().parseHex(checksumHex));
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
                .contentType("image/png")
                .contentLength(128L)
                .checksumSHA256(checksumBase64)
                .build());

        ObjectMetadata metadata = adapter.headObject("media/object");

        ArgumentCaptor<HeadObjectRequest> request = ArgumentCaptor.forClass(HeadObjectRequest.class);
        org.mockito.Mockito.verify(s3Client).headObject(request.capture());
        assertThat(request.getValue().checksumMode()).isEqualTo(ChecksumMode.ENABLED);
        assertThat(metadata.checksumSha256()).isEqualTo(checksumHex);
    }

    @Test
    void uploadInstructionsOmitBrowserControlledContentLength() throws Exception {
        PresignedPutObjectRequest signed = org.mockito.Mockito.mock(PresignedPutObjectRequest.class);
        when(signed.url()).thenReturn(URI.create("https://storage.example/upload").toURL());
        when(signed.expiration()).thenReturn(Instant.parse("2026-08-15T12:05:00Z"));
        when(presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(signed);

        UploadInstruction instruction = adapter.createUploadInstruction(
                "media/object",
                "image/png",
                128,
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

        ArgumentCaptor<PutObjectPresignRequest> request = ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        org.mockito.Mockito.verify(presigner).presignPutObject(request.capture());
        assertThat(request.getValue().putObjectRequest().contentLength()).isEqualTo(128L);
        assertThat(instruction.requiredHeaders())
                .containsKeys("Content-Type", "x-amz-checksum-sha256")
                .doesNotContainKey("Content-Length");
    }

    @Test
    void readUrlsUseTheConfiguredShortTtlAndInlineDisposition() throws Exception {
        PresignedGetObjectRequest signed = org.mockito.Mockito.mock(PresignedGetObjectRequest.class);
        when(signed.url()).thenReturn(URI.create("https://storage.example/read").toURL());
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(signed);

        String url = adapter.createReadUrl("media/object");

        ArgumentCaptor<GetObjectPresignRequest> request = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        org.mockito.Mockito.verify(presigner).presignGetObject(request.capture());
        assertThat(request.getValue().signatureDuration()).isEqualTo(Duration.ofMinutes(15));
        assertThat(request.getValue().getObjectRequest().responseContentDisposition()).isEqualTo("inline");
        assertThat(url).isEqualTo("https://storage.example/read");
    }
}
