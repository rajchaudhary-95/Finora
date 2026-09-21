package com.example.finora.ui.watchlist

import android.graphics.Color
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.finora.FinoraApp
import com.example.finora.R
import com.example.finora.databinding.FragmentStockChartBinding
import com.example.finora.util.StockPricePoint
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Locale

class StockChartFragment : Fragment() {

    private var _binding: FragmentStockChartBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WatchlistViewModel by activityViewModels {
        val app = requireActivity().application as FinoraApp
        WatchlistViewModel.Factory(
            app.watchlistRepository,
            app.portfolioRepository
        )
    }

    private var symbol: String = ""

    companion object {
        private const val ARG_SYMBOL = "ARG_SYMBOL"

        fun newInstance(symbol: String): StockChartFragment {
            return StockChartFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SYMBOL, symbol)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        symbol = arguments?.getString(ARG_SYMBOL) ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStockChartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupChartAppearance()
        observeData()
    }

    override fun onResume() {
        super.onResume()
        renderChart()
    }

    private fun setupChartAppearance() {
        val chart = binding.lineChart
        chart.description.isEnabled = false
        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(true)
        chart.setPinchZoom(true)
        chart.setDrawGridBackground(false)
        chart.legend.isEnabled = false

        // X-Axis formatting
        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
        xAxis.textSize = 10f
        xAxis.granularity = 1f

        // Y-Axis formatting
        val leftAxis = chart.axisLeft
        leftAxis.textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
        leftAxis.textSize = 10f
        leftAxis.setDrawGridLines(true)
        leftAxis.gridColor = ContextCompat.getColor(requireContext(), R.color.divider_color)
        leftAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return com.example.finora.util.CurrencyFormatter.formatAxis(value)
            }
        }

        chart.axisRight.isEnabled = false
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.watchlist.collect {
                    renderChart()
                }
            }
        }
    }

    private fun renderChart() {
        val app = requireActivity().application as FinoraApp
        val rawSamples = app.stockPriceHistoryStore.getSamples(symbol).toMutableList()

        val currentStock = viewModel.watchlist.value.firstOrNull {
            it.symbol.equals(symbol, ignoreCase = true)
        }

        // If no stored samples yet, but we have a valid lastKnownPrice, create an initial baseline sample
        if (rawSamples.isEmpty() && currentStock != null && currentStock.lastKnownPrice > 0.0) {
            val now = if (currentStock.lastFetchedAt > 0L) currentStock.lastFetchedAt else System.currentTimeMillis()
            // Estimate previous close if dayChangePercent is known
            val prevClose = currentStock.lastKnownPrice / (1.0 + (currentStock.dayChangePercent / 100.0))
            rawSamples.add(StockPricePoint(now - 14_400_000L, prevClose)) // 4 hours ago
            rawSamples.add(StockPricePoint(now, currentStock.lastKnownPrice))
        } else if (rawSamples.size == 1 && currentStock != null && currentStock.lastKnownPrice > 0.0) {
            val single = rawSamples[0]
            val prevClose = single.price / (1.0 + (currentStock.dayChangePercent / 100.0))
            rawSamples.add(0, StockPricePoint(single.timestamp - 7_200_000L, prevClose))
        }

        if (rawSamples.isEmpty()) {
            binding.lineChart.visibility = View.GONE
            binding.layoutChartEmpty.visibility = View.VISIBLE
            binding.tvChartInfo.text = "No samples collected yet"
            return
        }

        binding.lineChart.visibility = View.VISIBLE
        binding.layoutChartEmpty.visibility = View.GONE
        binding.tvChartInfo.text = "Showing ${rawSamples.size} price samples"

        val entries = mutableListOf<Entry>()
        val timestamps = mutableListOf<Long>()

        rawSamples.forEachIndexed { index, point ->
            entries.add(Entry(index.toFloat(), point.price.toFloat()))
            timestamps.add(point.timestamp)
        }

        binding.lineChart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val index = value.toInt()
                return if (index in timestamps.indices) {
                    DateFormat.format("HH:mm", Date(timestamps[index])).toString()
                } else {
                    ""
                }
            }
        }

        val tealColor = ContextCompat.getColor(requireContext(), R.color.primary_teal)
        val dataSet = LineDataSet(entries, "Price").apply {
            color = tealColor
            setCircleColor(tealColor)
            lineWidth = 2.5f
            circleRadius = 4f
            setDrawCircleHole(true)
            circleHoleColor = Color.WHITE
            circleHoleRadius = 2f
            setDrawValues(false)
            setDrawFilled(true)
            fillColor = ContextCompat.getColor(requireContext(), R.color.mint_accent_light)
            fillAlpha = 90
            mode = LineDataSet.Mode.CUBIC_BEZIER
            highLightColor = ContextCompat.getColor(requireContext(), R.color.accent_mint)
            setDrawHighlightIndicators(true)
        }

        binding.lineChart.data = LineData(dataSet)
        binding.lineChart.animateX(400)
        binding.lineChart.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
