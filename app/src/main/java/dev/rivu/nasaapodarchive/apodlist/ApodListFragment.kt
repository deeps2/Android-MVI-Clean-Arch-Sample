package dev.rivu.nasaapodarchive.apodlist

import android.os.Bundle
import android.view.View
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jakewharton.rxbinding3.recyclerview.scrollEvents
import com.jakewharton.rxbinding3.swiperefreshlayout.refreshes
import com.jakewharton.rxbinding3.view.detaches
import dev.rivu.nasaapodarchive.R
import dev.rivu.nasaapodarchive.base.BaseFragment
import dev.rivu.nasaapodarchive.domain.utils.format
import dev.rivu.nasaapodarchive.domain.utils.parseDate
import dev.rivu.nasaapodarchive.presentation.apodlist.ApdListViewModelFactory
import dev.rivu.nasaapodarchive.presentation.apodlist.ApodListIntent
import dev.rivu.nasaapodarchive.presentation.apodlist.ApodListState
import dev.rivu.nasaapodarchive.presentation.apodlist.ApodListViewModel
import dev.rivu.nasaapodarchive.presentation.apodlist.model.ApodViewData
import dev.rivu.nasaapodarchive.presentation.base.MviView
import dev.rivu.nasaapodarchive.utils.get
import dev.rivu.nasaapodarchive.utils.gone
import dev.rivu.nasaapodarchive.utils.isVisible
import dev.rivu.nasaapodarchive.utils.visible
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.fragment_apodlist.*
import java.util.*
import javax.inject.Inject

class ApodListFragment : BaseFragment(), MviView<ApodListIntent, ApodListState> {

    @Inject
    lateinit var viewModelFactory: ApdListViewModelFactory

    private val clearClickPublisher: PublishSubject<ApodListIntent.ClearClickIntent> by lazy {
        PublishSubject.create<ApodListIntent.ClearClickIntent>()
    }


    private val adapter by lazy {
        ApodListAdapter()
    }

    private lateinit var layoutManager: GridLayoutManager

    private val today: String by lazy {
        Calendar.getInstance().time.format()
    }

    private val apodListViewModel: ApodListViewModel by lazy {
        ViewModelProviders.of(this, viewModelFactory)
            .get(ApodListViewModel::class.java)
    }

    override fun layoutId(): Int = R.layout.fragment_apodlist

    override fun initView() {
        layoutManager = GridLayoutManager(context, 2, RecyclerView.VERTICAL, false)
        rvApodlist.layoutManager = layoutManager
        rvApodlist.adapter = adapter
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return adapter.getItemViewType(position)
            }

        }
    }

    private fun showImageDetailsAndClear(view: View, apodViewData: ApodViewData) {
        clearClickPublisher.onNext(ApodListIntent.ClearClickIntent)
        val bundle = Bundle()
        bundle.putParcelable("apodViewData", apodViewData)
        val extras = FragmentNavigatorExtras(
            view to apodViewData.date.format()
        )
        findNavController()
            .navigate(R.id.action_list_to_detail, bundle, null, extras)
    }

    override fun bind() {
        apodListViewModel.states()
            .observe(viewLifecycleOwner, Observer<ApodListState> { state ->
                render(state)
            })
        apodListViewModel.processIntents(intents())
    }

    override fun intents(): Observable<ApodListIntent> {
        return Observable.mergeArray(
            Observable.just(ApodListIntent.InitialIntent(today, 10)),
            swipeRefreshApodlist.refreshes().map {
                ApodListIntent.RefreshIntent(today, layoutManager.itemCount)
            },
            adapter.clickEvent
                .map { clickData ->
                    ApodListIntent.ClickIntent(
                        clickedViewPosition = clickData.position,
                        date = clickData.apodViewData.date.format()
                    )
                }
                .takeUntil(rvApodlist.detaches()),
            clearClickPublisher,
            rvApodlist.scrollEvents()
                .filter {
                    val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
                    val totalItems = layoutManager.itemCount
                    lastVisibleItem >= totalItems - 1
                }
                .map {
                    val lastDate = adapter.apodItemList.last().date
                    val calendar = Calendar.getInstance()
                    calendar.time = lastDate
                    calendar.add(Calendar.DAY_OF_MONTH, -1)
                    val nextPageStartDate = calendar.time.format()
                    ApodListIntent.LoadMoreIntent(
                        startDate = nextPageStartDate,
                        count = 4
                    ) as ApodListIntent
                }
        )
    }

    private fun showLoading() {
        hideErrorView()
        if (swipeRefreshApodlist.isVisible()) {
            swipeRefreshApodlist.isRefreshing = true
        } else {
            progress.visible()
        }

    }

    private fun hideLoading() {
        if (swipeRefreshApodlist.isVisible()) {
            swipeRefreshApodlist.isRefreshing = false
        } else {
            progress.gone()
            swipeRefreshApodlist.visible()
        }

    }

    private fun showError(errorMessage: String) {
        errorView.visible()
        errorView.setErrorMessage(errorMessage)
    }

    private fun hideErrorView() {
        if (errorView.isVisible()) {
            errorView.gone()
        }
    }

    override fun render(state: ApodListState) {
        if (state.isLoading) {
            showLoading()
            return
        } else {
            hideLoading()
        }
        if (state.isError) {
            showError(state.errorMessage)
            return
        } else {
            hideErrorView()
        }
        if (state.isLoadingMore) {
            adapter.showLoadingMore()
        } else {
            adapter.hideLoadingMore()
        }
        if (state.apodList.isNotEmpty()) {
            adapter.updateItems(apodItemList = state.apodList)
        }
        if (!state.detailDate.isBlank() && state.clickedViewPosition in state.apodList.indices) {
            showImageDetailsAndClear(
                layoutManager.findViewByPosition(state.clickedViewPosition)!!,
                state.apodList[state.detailDate]!!
            )
        }
    }
}
