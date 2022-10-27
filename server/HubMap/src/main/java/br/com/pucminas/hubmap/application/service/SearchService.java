package br.com.pucminas.hubmap.application.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StopWatch;

import br.com.pucminas.hubmap.domain.indexing.Histogram;
import br.com.pucminas.hubmap.domain.indexing.HistogramItem;
import br.com.pucminas.hubmap.domain.indexing.HistogramItemRepository;
import br.com.pucminas.hubmap.domain.indexing.HistogramRepository;
import br.com.pucminas.hubmap.domain.indexing.NGram;
import br.com.pucminas.hubmap.domain.indexing.search.Search;
import br.com.pucminas.hubmap.utils.PageableUtils;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Service
public class SearchService {

	private static final Integer PAGE_SIZE = 5;

	private HistogramRepository histogramRepository;

	private HistogramService histogramService;

	private HistogramItemRepository histogramItemRepository;

	public SearchService(HistogramRepository histogramRepository, HistogramService histogramService,
			HistogramItemRepository histogramItemRepository) {
		super();
		this.histogramRepository = histogramRepository;
		this.histogramService = histogramService;
		this.histogramItemRepository = histogramItemRepository;
	}

	@Transactional(readOnly = true)
	public List<Integer> search(Search search) throws InterruptedException, ExecutionException {

		Pageable pageable = PageableUtils.getPageableFromParameters(0, PAGE_SIZE);
		List<HistogramSearch> histogramsSimilarity = new ArrayList<>();
		
		StopWatch measure = new StopWatch();
		
		measure.start("GenerateSearchHist");
		Histogram searchHist = histogramService.generateSearchHistogram(search);
		search.setHistogram(searchHist);
		
		measure.stop();

		measure.start("CalculateSimilarity");
		Page<Histogram> histograms = histogramRepository.findByInitilized(true, pageable);

		while (true) {
			histograms.stream()
				.parallel()
				.forEach(histogram -> {
					double similarity = 0.0;
					
					try {
						similarity = compareHistograms(search.getHistogram(), histogram);
					} catch (InterruptedException | ExecutionException e) {
						throw new RuntimeException(e);
					}

					if (similarity > 0.0) {
						HistogramSearch histSearch = new HistogramSearch();
						histSearch.setPostId(histogram.getPost().getId());
						histSearch.setSimilarity(similarity);
						histogramsSimilarity.add(histSearch);
					}
				});

			if (histograms.hasNext()) {
				histograms = histogramRepository.findAll(pageable.next());
			} else {
				break;
			}
		}
		measure.stop();
		
		Collections.sort(histogramsSimilarity, new Comparator<>() {
			@Override
			public int compare(HistogramSearch o1, HistogramSearch o2) {
				return -1 * o1.getSimilarity().compareTo(o2.getSimilarity());
			}
		});
			
		System.out.println(measure.prettyPrint());
		return histogramsSimilarity.stream()
				.map(h -> h.getPostId())
				.collect(Collectors.toList());
	}

	private double compareHistograms(Histogram hist1, Histogram hist2) throws InterruptedException, ExecutionException {

		Set<NGram> vocab = joinHistograms(hist1, hist2);
		Double[] dissimilarityCoefficient = calculateDissimilarityCoefficient(hist1, hist2, vocab);
		
		int n = vocab.size();
		double dc = dissimilarityCoefficient[0] / dissimilarityCoefficient[1];
		double s = dissimilarityCoefficient[2];
		double t = dissimilarityCoefficient[3];
		
		double similarity = 0.5 * ((s / n) + ((t * s) / (t * s + dc)));
		
		return similarity;
	}
	
	private Double[] calculateDissimilarityCoefficient(Histogram hist1, Histogram hist2,
			Set<NGram> vocab) {

		double divisor = 0.0;
		double dividend = 0.0;
		double t = 0;
		double s = 0;

		for (NGram nGram : vocab) {
			HistogramItem item1 = hist1.getItemFromHistogram(nGram);
			Optional<HistogramItem> item2 = histogramItemRepository.findByKeyAndOwner(nGram, hist2);
			double tfIdfH1 = item1 != null ? item1.getTfidf() : 0.0;
			double tfIdfH2 = item2.isPresent() ? item2.get().getTfidf() : 0.0;

			divisor += tfIdfH1 + tfIdfH2;
			dividend += Math.abs(tfIdfH1 - tfIdfH2);
			
			if (tfIdfH1 != 0.0 && tfIdfH2 != 0.0) {
				s++;
			}
			
			if (tfIdfH1 != 0.0 || tfIdfH2 != 0.0) {
				t++;
			}
		}

		return new Double[] { divisor * 0.5, dividend, s, t };
	}

	private Set<NGram> joinHistograms(Histogram hist1, Histogram hist2) {
		Set<NGram> nGrams = new HashSet<>();

		hist2.getHistogram().forEach(i -> nGrams.add(i.getKey()));
		hist1.getHistogram().forEach(i -> nGrams.add(i.getKey()));

		return nGrams;
	}

	@Getter
	@Setter
	@EqualsAndHashCode(onlyExplicitlyIncluded = true)
	private class HistogramSearch {

		private Double similarity;

		@EqualsAndHashCode.Include
		private Integer postId;
	}
}
