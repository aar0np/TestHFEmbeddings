package testembeddings;

import java.io.BufferedReader;
//import java.io.BufferedWriter;
import java.io.FileReader;
//import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.data.CqlVector;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.huggingface.HuggingFaceEmbeddingModel;

public class TestEmbeddings {

	private record Product (String id, String name, String productGroup,
			UUID parentId, UUID categoryId, Set<String> images) {
		
	}
	
	private static CqlSession session;
	private static PreparedStatement selectProduct;
	private static PreparedStatement insertVector;	
	
	private static final UUID EMPTY_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");
	
	public static void main(String[] args) {

        EmbeddingModel embeddingModel = HuggingFaceEmbeddingModel.builder()
                .accessToken(System.getenv("HF_API_KEY"))
                .modelId("sentence-transformers/all-MiniLM-L6-v2")
                .waitForModel(true)
                .timeout(Duration.ofSeconds(60))
                .build();
        
		// connect to Astra DB
		AstraConnection conn = new AstraConnection();
		session = conn.getCqlSession();
        
		// prepared statements
	    selectProduct = session.prepare("SELECT product_id, name, product_group, images FROM product WHERE product_id = ?");
		insertVector = session.prepare("INSERT INTO product_vectors (product_id, name, product_group, parent_id, category_id, images, product_vector) VALUES (?,?,?,?,?,?,?)");
		
        boolean headerRow = true;

        try {
        	BufferedReader reader = new BufferedReader(new FileReader("data/product_id_name.csv"));
        	//BufferedWriter writer = new BufferedWriter(new FileWriter("product_embeddings.csv"));
        	
        	// write header
        	//writer.write("product_id|name|product_vector\n");
        	
        	String dataLine = reader.readLine();
        	StringBuilder builder = new StringBuilder();
        	
        	while (dataLine != null) {
        		
        		if (!headerRow) {
        			// [0] = product_id
        			// [1] = name
        			String[] columnData = dataLine.split(",");
        			String productId = columnData[0];

        			Product prod = getProduct(productId);
        			
        			Embedding vEmbedding = embeddingModel.embed(prod.name);
        			List<Float> vector = vEmbedding.vectorAsList();
        			
        			setProductVector(prod,CqlVector.newInstance(vector));
        			
        			builder = new StringBuilder(productId);
        			builder.append("|");
        			builder.append(prod.name);
        			builder.append("|");
        			builder.append(vector);
        			
        			System.out.printf("%s\n", builder);
        			
        			//writer.write(builder + "\n");
        		} else {
        			// skip the CSV header
        			headerRow = false;
        		}
        		
        		dataLine = reader.readLine();
        	}
        	
        	reader.close();
        	//writer.close();
        	
        } catch (IOException ex) {
        	ex.printStackTrace();
        }
    }
	
	private static Product getProduct(String id) {

        BoundStatement selectProductBound = selectProduct.bind(id);
        
        ResultSet rs = session.execute(selectProductBound);
        Row row = rs.one();
		
        return new Product(id, row.getString("name"), row.getString("product_group"), EMPTY_UUID,
        		EMPTY_UUID, row.getSet("images", String.class));
	}
	
	private static void setProductVector(Product prod, CqlVector<Float> vector) {
		BoundStatement insertVectorBound = insertVector.bind(prod.id, prod.name, prod.productGroup, prod.parentId,
				prod.categoryId, prod.images, vector);
		
		session.execute(insertVectorBound);
	}
}