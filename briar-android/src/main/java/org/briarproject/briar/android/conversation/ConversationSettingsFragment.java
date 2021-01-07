package org.briarproject.briar.android.conversation;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;

import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.TransactionManager;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.api.autodelete.AutoDeleteManager;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelProviders;

import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.briar.android.util.UiUtils.observeOnce;
import static org.briarproject.briar.api.autodelete.AutoDeleteConstants.NO_AUTO_DELETE_TIMER;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class ConversationSettingsFragment extends BaseFragment {

	public static final String TAG =
			ConversationSettingsFragment.class.getName();

	private static final Logger LOG =
			Logger.getLogger(ConversationSettingsFragment.class.getName());

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	@Inject
	@DatabaseExecutor
	Executor dbExecutor;

	@Inject
	TransactionManager db;

	@Inject
	AutoDeleteManager autoDeleteManager;

	private ConversationSettingsActivity listener;

	private Switch switchDisappearingMessages;

	private volatile boolean disappearingMessages = false;

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		listener = (ConversationSettingsActivity) context;
		listener.getActivityComponent().inject(this);
	}

	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		View contentView =
				inflater.inflate(R.layout.fragment_conversation_settings,
						container, false);

		switchDisappearingMessages =
				contentView.findViewById(R.id.switchDisappearingMessages);

		switchDisappearingMessages
				.setOnCheckedChangeListener((button, value) -> {
					viewModel.setAutoDeleteTimerEnabled(value);
				});

		viewModel = ViewModelProviders.of(requireActivity(), viewModelFactory)
				.get(ConversationViewModel.class);

		return contentView;
	}

	private ConversationViewModel viewModel;

	@Override
	public void onStart() {
		super.onStart();
		switchDisappearingMessages.setEnabled(false);
		loadSettings();
	}

	private void loadSettings() {
		observeOnce(viewModel.getContact(), this, c -> {
			dbExecutor.execute(() -> {
				try {
					db.transaction(false, txn -> {
						long timer = autoDeleteManager
								.getAutoDeleteTimer(txn, c.getId());
						disappearingMessages = timer != NO_AUTO_DELETE_TIMER;
					});
					displaySettings();
				} catch (DbException e) {
					logException(LOG, WARNING, e);
				}
			});
		});
	}

	private void displaySettings() {
		listener.runOnUiThreadUnlessDestroyed(() -> {
			switchDisappearingMessages.setChecked(disappearingMessages);
			switchDisappearingMessages.setEnabled(true);
		});
	}

}