package testembeddings;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.Duration;
import java.util.List;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.huggingface.HuggingFaceEmbeddingModel;

public class TestEmbeddings {

	public static void main(String[] args) {

        EmbeddingModel embeddingModel = HuggingFaceEmbeddingModel.builder()
                .accessToken(System.getenv("HF_API_KEY"))
                .modelId("sentence-transformers/all-MiniLM-L6-v2")
                .waitForModel(true)
                .timeout(Duration.ofSeconds(60))
                .build();
        
        boolean headerRow = true;

        try {
        	BufferedReader reader = new BufferedReader(new FileReader("data/product_id_name.csv"));
        	
        	String dataLine = reader.readLine();
        	
        	while (dataLine != null) {
        		
        		if (!headerRow) {
        			// [0] = product_id
        			// [1] = name
        			String[] columnData = dataLine.split(",");
        			String productId = columnData[0];
        			String prodName = columnData[1];
        			
        			Embedding vEmbedding = embeddingModel.embed(prodName);
        			List<Float> vector = vEmbedding.vectorAsList();
        			
        			System.out.printf("%s, %s, %s\n", productId, prodName, vector);
        		} else {
        			// skip the CSV header
        			headerRow = false;
        		}
        		
        		dataLine = reader.readLine();
        	}
        	
        	reader.close();
        	
        } catch (IOException ex) {
        	ex.printStackTrace();
        }
        
        //Embedding embedding = embeddingModel.embed("Hello, how are you?");
        //System.out.println(embedding);
        //System.out.println(embedding.vectorAsList().size());
        //System.out.println(embedding.vector());
	}
}