package com.kuky.demo.wan.android.ui.collectedwebsites

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.kuky.demo.wan.android.R
import com.kuky.demo.wan.android.base.*
import com.kuky.demo.wan.android.databinding.FragmentCollectedWebsitesBinding
import com.kuky.demo.wan.android.ui.app.AppViewModel
import com.kuky.demo.wan.android.ui.websitedetail.WebsiteDetailFragment
import com.kuky.demo.wan.android.utils.Injection
import com.kuky.demo.wan.android.widget.ErrorReload
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import org.jetbrains.anko.selector
import org.jetbrains.anko.toast

/**
 * @author kuky.
 * @description
 */
class CollectedWebsitesFragment : BaseFragment<FragmentCollectedWebsitesBinding>() {

    private val mAppViewModel by lazy {
        getSharedViewModel(AppViewModel::class.java)
    }

    private val mViewModel by lazy {
        ViewModelProvider(requireActivity(), Injection.provideCollectedWebsitesViewModelFactory())
            .get(CollectedWebsitesViewModel::class.java)
    }

    private var mFavouriteJob: Job? = null

    private val mAdapter by lazy { CollectedWebsitesAdapter() }

    private val editSelector by lazy { arrayListOf(resources.getString(R.string.del_website), resources.getString(R.string.edit_website)) }

    override fun actionsOnViewInflate() = fetchWebSitesData()

    override fun getLayoutId(): Int = R.layout.fragment_collected_websites

    override fun initFragment(view: View, savedInstanceState: Bundle?) {
        mBinding?.let { binding ->
            binding.refreshColor = R.color.colorAccent
            binding.refreshListener = SwipeRefreshLayout.OnRefreshListener {
                fetchWebSitesData()
            }

            binding.adapter = mAdapter

            binding.listener = OnItemClickListener { position, _ ->
                mAdapter.getItemData(position)?.let {
                    WebsiteDetailFragment.viewDetail(
                        mNavController,
                        R.id.action_collectionFragment_to_websiteDetailFragment,
                        it.link
                    )
                }
            }

            binding.longListener = OnItemLongClickListener { position, _ ->
                mAdapter.getItemData(position)?.let { data ->
                    context?.selector(items = editSelector) { _, i ->
                        when (i) {
                            0 -> launch { removeFavouriteWebsite(data.id) }

                            1 -> CollectedWebsiteDialogFragment
                                .createCollectedDialog(true, data.id, data.name, data.link)
                                .showAllowStateLoss(childFragmentManager, "edit_website")
                        }
                    }
                }
            }

            binding.errorReload = ErrorReload { fetchWebSitesData() }

            binding.gesture = DoubleClickListener {
                singleTap = {
                    CollectedWebsiteDialogFragment
                        .createCollectedDialog(false)
                        .showAllowStateLoss(childFragmentManager, "new_website")
                }
            }
        }
    }

    fun scrollToTop() = mBinding?.websiteList?.scrollToTop()

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun removeFavouriteWebsite(id: Int) {
        mViewModel.deleteFavouriteWebsite(id).catch {
            context?.toast(R.string.no_network)
        }.onStart {
            mAppViewModel.showLoading()
        }.onCompletion {
            mAppViewModel.dismissLoading()
        }.collectLatest {
            it.handleResult {
                context?.toast(R.string.remove_favourite_succeed)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun fetchWebSitesData() {
        mFavouriteJob?.cancel()
        mFavouriteJob = launch {
            mViewModel.getWebsites().catch {
                pageState(NetworkState.FAILED)
            }.onStart {
                pageState(NetworkState.RUNNING)
            }.collectLatest {
                pageState(NetworkState.SUCCESS)
                mBinding?.emptyStatus = it.isNullOrEmpty()
                mAdapter.update(it)
            }
        }
    }

    private fun pageState(state: NetworkState) = mBinding?.run {
        refreshing = state == NetworkState.RUNNING
        loadingStatus = state == NetworkState.RUNNING
        errorStatus = state == NetworkState.FAILED
    }
}