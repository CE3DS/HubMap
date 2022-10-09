package br.com.pucminas.hubmap.domain.indexing;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;

import br.com.pucminas.hubmap.domain.post.Post;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@SuppressWarnings("serial")
@Entity
@Setter
@Getter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Histogram implements Serializable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@EqualsAndHashCode.Include
	private Integer id;
	
	@OneToOne(cascade = CascadeType.ALL)
	@JoinColumn(name = "POST_ID")
	private Post post;

	@OneToMany(mappedBy = "owner", fetch = FetchType.EAGER, cascade = CascadeType.ALL)
	@ToString.Exclude
	private Set<HistogramItem> histogram = new LinkedHashSet<>();

	public Histogram(Post post) {
		this.post = post;
	}
	
	public void initialize(List<String> bagOfWords) {
		HistogramItem item;
		int counter;

		for (String word : bagOfWords) {
			if (!isInHistogram(word)) {
				counter = countWords(bagOfWords, word);
				item = new HistogramItem();
				item.setCount(counter);
				histogram.add(item);
			}
		}
	}
	
	public void initialize(List<String> bagOfWords, Set<Histogram> histograms) {
		HistogramItem item;
		int counter;

		for (String word : bagOfWords) {
			if (!isInHistogram(word)) {
				counter = countWords(bagOfWords, word);
				item = new HistogramItem();
				item.setCount(counter);
				histogram.add(item);
			}
		}
		
		calculateTfIdf(histograms);
	}

	public void refreshHistograms(Set<Histogram> histograms) {
		List<String> words = new ArrayList<>();
		int counter;
		
		for (HistogramItem item : histogram) {
			words.add(item.getKey().getGram());
		}
		
		for (HistogramItem item : histogram) {
			counter = countWords(words, item.getKey().getGram());
			item.setCount(counter);
		}

		calculateTfIdf(histograms);
	}

	public boolean isInHistogram(String term) {
		for (HistogramItem item : histogram) {
			if (item.getKey().getGram().equals(term)) {
				return true;
			}
		}

		return false;
	}

	private void calculateTfIdf(Set<Histogram> histograms) {
		double tf;
		double idf;

		for (HistogramItem item : histogram) {
			tf = calculateTf(item.getCount());
			idf = calculateIdf(item.getKey().getGram(), histograms);
			item.setTfidf(tf * idf);
		}
	}

	private double calculateTf(int count) {
		return count / histogram.size();
	}

	private double calculateIdf(String term, Set<Histogram> histograms) {
		int counter = 0;
		boolean appear;

		for (Histogram hist : histograms) {
			counter = 0;
			appear = false;

			for (HistogramItem item : hist.getHistogram()) {
				if (item.getKey().getGram().equals(term)) {
					appear = true;
					break;
				}
			}

			if (appear) {
				counter++;
			}
		}

		counter = counter == 0 ? 1 : counter;

		return 1 + Math.log(histograms.size() / counter);
	}

	private int countWords(List<String> bagOfWords, String term) {

		return Collections.frequency(bagOfWords, term);
	}
}
