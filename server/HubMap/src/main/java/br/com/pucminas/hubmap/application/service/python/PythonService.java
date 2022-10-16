package br.com.pucminas.hubmap.application.service.python;

import static br.com.pucminas.hubmap.utils.LoggerUtils.getLoggerFromClass;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.List;

import org.springframework.stereotype.Service;

import jep.Interpreter;
import jep.SharedInterpreter;

@Service
public class PythonService {
	
	@SuppressWarnings("unchecked")
	public List<String> getBagOfWords(String sentence) throws IOException {
		
		File file;
		
		URL res = getClass().getClassLoader().getResource("python/spacy.py");
		try {
			file = Paths.get(res.toURI()).toFile();
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
		
		String fullPath = file.getAbsolutePath();
		
		getLoggerFromClass(getClass()).info("2 - Path spacy.py" + fullPath);
		
		List<String> tokens = null;
		
		try (Interpreter interp = new SharedInterpreter()) {

			sentence = Normalizer.normalize(sentence, Normalizer.Form.NFKD).replaceAll("\\p{M}", "");

			interp.set("sentence", sentence);
			interp.runScript(fullPath);

			tokens = interp.getValue("bagOfWords", List.class);
		}
		
		return tokens;
	}
}
