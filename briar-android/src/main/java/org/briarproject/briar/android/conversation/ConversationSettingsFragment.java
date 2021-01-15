package org.briarproject.briar.android.conversation;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.fragment.BaseFragment;

import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.appcompat.widget.SwitchCompat;
import androidx.lifecycle.ViewModelProvider;

import static org.briarproject.briar.api.autodelete.AutoDeleteConstants.NO_AUTO_DELETE_TIMER;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class ConversationSettingsFragment extends BaseFragment {

	public static final String TAG =
			ConversationSettingsFragment.class.getName();

	private static final Logger LOG = Logger.getLogger(TAG);

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private ConversationSettingsActivity listener;
	private ConversationViewModel viewModel;
	private SwitchCompat switchDisappearingMessages;

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
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
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
		switchDisappearingMessages.setEnabled(false);
		switchDisappearingMessages
				.setOnCheckedChangeListener((button, value) -> viewModel
						.setAutoDeleteTimerEnabled(value));

		TextView buttonLearnMore =
				contentView.findViewById(R.id.buttonLearnMore);
		buttonLearnMore.setOnClickListener(e -> showLearnMoreDialog());

		viewModel = new ViewModelProvider(this, viewModelFactory)
				.get(ConversationViewModel.class);

		viewModel.getAutoDeleteTimer()
				.observe(getViewLifecycleOwner(), timer -> {
					boolean disappearingMessages =
							timer != NO_AUTO_DELETE_TIMER;
					switchDisappearingMessages.setChecked(disappearingMessages);
					switchDisappearingMessages.setEnabled(true);
				});

		return contentView;
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.help_action, menu);
		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.action_help) {
			showLearnMoreDialog();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void showLearnMoreDialog() {
		ConversationSettingsLearnMoreDialog
				dialog = new ConversationSettingsLearnMoreDialog();
		dialog.show(requireActivity().getSupportFragmentManager(),
				ConversationSettingsLearnMoreDialog.TAG);
	}

}