package com.codespade.stream.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.codespade.proto.StreamGrpc;
import com.codespade.proto.StreamOuterClass;
import com.codespade.proto.StreamOuterClass.BlockIdRequest;
import com.codespade.proto.StreamOuterClass.BlockIdResponse;
import com.codespade.proto.StreamOuterClass.VerifyHashResponse;
import com.codespade.stream.client.config.GrpcConfig;
import com.codespade.stream.client.config.StreamGrpc1Config;
import com.codespade.stream.client.config.StreamGrpc2Config;
import com.codespade.stream.client.model.Response;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;

@RestController
@RequestMapping("/codespade")
public class StreamingController {
	@Autowired
	GrpcConfig grpcConfig;
	
	@Autowired
	StreamGrpc1Config grpcServer1Config;
	
	@Autowired
	StreamGrpc2Config grpcServer2Config;
	
	private final static String VERIFY_HASH_REST_URL = "http://localhost:9998/codespade/verify-hash";
	private final static String BLOCK_ID_REST_URL = "http://localhost:9997/codespade/block-id";
	
	private StreamGrpc.StreamStub getStub(ManagedChannel channel, int deadLine){
		return StreamGrpc.newStub(channel)
				.withDeadlineAfter(deadLine, TimeUnit.SECONDS);
	}
	
	private StreamGrpc.StreamBlockingStub getBlockingStub(ManagedChannel channel, int deadLine){
		return StreamGrpc.newBlockingStub(channel)
				.withDeadlineAfter(deadLine, TimeUnit.SECONDS);
	}
	
	@PostMapping("/api/upload-file/stream")
	public Response UploadFile(@RequestParam("file") MultipartFile file) throws IOException, InterruptedException {
		InputStream fis = new ByteArrayInputStream(file.getBytes());

        // Finds the workbook instance for XLSX file
        XSSFWorkbook myWorkBook = new XSSFWorkbook (fis);
        
        // Return first sheet from the XLSX workbook
        XSSFSheet mySheet = myWorkBook.getSheetAt(0);
       
        // Get iterator to all the rows in current sheet
        Iterator<Row> rowIterator = mySheet.iterator();
       
        // Skip first row
        if(rowIterator.hasNext()) {
        	rowIterator.next();
        }

        Response response = Response.builder()
        		.status("success")
        		.totalId(0)
        		.verifiedId(0)
        		.blockedId(0)
        		.build();
        
        // Creating channel
        ManagedChannel channel1 = grpcConfig.getChannel(grpcServer1Config);
        ManagedChannel channel2 = grpcConfig.getChannel(grpcServer2Config);
		StreamGrpc.StreamStub stub = getStub(channel1, grpcServer1Config.getDeadlineAfter());
		StreamGrpc.StreamBlockingStub blockingStub = getBlockingStub(channel2, grpcServer2Config.getDeadlineAfter());

		final CountDownLatch finishLatch = new CountDownLatch(mySheet.getLastRowNum());
		StreamObserver<StreamOuterClass.VerifyHashRequest> requestStreamObserver = stub.verifyHash(new StreamObserver<StreamOuterClass.VerifyHashResponse>() {
			
			@Override
			public void onNext(VerifyHashResponse value) {
				if(value.getStatus().equals("failed")) {
					System.out.println("[GRPC] Failed to verify hash on id: " + value.getId());
					BlockIdRequest blockIdRequest = BlockIdRequest.newBuilder()
							.setId(value.getId())
							.build();
					System.out.println("[GRPC] Sending request to block id...");
					try {
						BlockIdResponse blockIdResponse = blockingStub.blockId(blockIdRequest);
					
						System.out.println("[GRPC] Status of ID ["+value.getId()+"]: " + blockIdResponse.getStatus());
					}catch(Exception e) {
						e.printStackTrace();
					}
					response.setBlockedId(response.getBlockedId() + 1);
					finishLatch.countDown();
				}else {
					response.setVerifiedId(response.getVerifiedId() + 1);
					finishLatch.countDown();
				}
			}
			
			@Override
			public void onError(Throwable t) {
				System.out.println("onError: " + t.getLocalizedMessage());
			}
			
			@Override
			public void onCompleted() {
				
			}
		});
		
		try {
	        // Traversing over each row of XLSX file
	        while (rowIterator.hasNext()) {
	            Row row = rowIterator.next();
	
	            String id = row.getCell(0).getStringCellValue();
	            String hash = row.getCell(1).getStringCellValue();
	       
	            response.setTotalId(response.getTotalId() + 1);
	            
	            StreamOuterClass.VerifyHashRequest verifyHashRequest = StreamOuterClass.VerifyHashRequest.newBuilder()
	            		.setId(id)
	            		.setHash(hash)
	            		.build();
	            requestStreamObserver.onNext(verifyHashRequest);
	        }
	        
	        myWorkBook.close();
	        fis.close();
		}catch (Exception e) {
			requestStreamObserver.onError(e);
			e.printStackTrace();
			return null;
		}

        requestStreamObserver.onCompleted();
        finishLatch.await(10, TimeUnit.SECONDS);
        
        channel1.shutdown();
        channel2.shutdown();
        
		return response;
	}
	
	@SuppressWarnings({ "unchecked", "resource" })
	@PostMapping("/api/upload-file/rest")
	public Response UploadFile2(@RequestParam("file") MultipartFile file) throws IOException {
		InputStream fis = new ByteArrayInputStream(file.getBytes());

        // Finds the workbook instance for XLSX file
        XSSFWorkbook myWorkBook = new XSSFWorkbook (fis);
        
        // Return first sheet from the XLSX workbook
        XSSFSheet mySheet = myWorkBook.getSheetAt(0);
       
        // Get iterator to all the rows in current sheet
        Iterator<Row> rowIterator = mySheet.iterator();
       
        // Skip first row
        if(rowIterator.hasNext()) {
        	rowIterator.next();
        }
        
        Response response = Response.builder()
        		.status("success")
        		.totalId(0)
        		.verifiedId(0)
        		.blockedId(0)
        		.build();
        ObjectMapper om = new ObjectMapper();
        OkHttpClient client = new OkHttpClient();
        
        while (rowIterator.hasNext()) {
            Row row = rowIterator.next();

            String id = row.getCell(0).getStringCellValue();
            String hash = row.getCell(1).getStringCellValue();
            
            response.setTotalId(response.getTotalId() + 1);
            
            HashMap<String, String> requestBody = new HashMap<String, String>();
            requestBody.put("id", id);
            requestBody.put("hash", hash);
            
            RequestBody verifyHashRequestBody = RequestBody.create(om.writeValueAsString(requestBody), 
            	      okhttp3.MediaType.parse("application/json"));

            okhttp3.Request verifyHashRequest = new okhttp3.Request.Builder()
            	      .url(VERIFY_HASH_REST_URL)
            	      .post(verifyHashRequestBody)
            	      .build();

            Call call = client.newCall(verifyHashRequest);
            okhttp3.Response responseHttp = call.execute();
            HashMap<String, String> responseBody = om.readValue(responseHttp.body().string(), HashMap.class);
        
            if(responseBody.get("status").equals("failed")) {
            	System.out.println("[HTTP] Failed to verify hash on id: " +responseBody.get("id"));
				
				RequestBody blockIdRequestBody = RequestBody.create(om.writeValueAsString(requestBody), 
	            	      okhttp3.MediaType.parse("application/json"));

	            okhttp3.Request blockIdRequest = new okhttp3.Request.Builder()
	            	      .url(BLOCK_ID_REST_URL)
	            	      .post(blockIdRequestBody)
	            	      .build();

				System.out.println("[HTTP] Sending request to block id...");

	            call = client.newCall(blockIdRequest);
	            responseHttp = call.execute();
	            responseBody = om.readValue(responseHttp.body().string(), HashMap.class);
	            
				System.out.println("[HTTP] Status of ID ["+responseBody.get("id")+"]: " + responseBody.get("status"));
				
				response.setBlockedId(response.getBlockedId() + 1);
            }else {
            	response.setVerifiedId(response.getVerifiedId() + 1);
            }
        }
        
        return response;
	}
}
