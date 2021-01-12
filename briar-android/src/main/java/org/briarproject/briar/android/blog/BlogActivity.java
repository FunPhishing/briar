package org.briarproject.briar.android.blog;

import android.content.Intent;
import android.os.Bundle;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.fragment.BaseFragment.BaseFragmentListener;
import org.briarproject.briar.android.sharing.BlogSharingStatusActivity;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class BlogActivity extends BriarActivity
		implements BaseFragmentListener {

	@Inject
	ViewModelProvider.Factory viewModelFactory;
	@Inject
	BlogController blogController;

	private BlogViewModel viewModel;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
		viewModel = new ViewModelProvider(this, viewModelFactory)
				.get(BlogViewModel.class);
	}

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);

		// GroupId from Intent
		Intent i = getIntent();
		byte[] b = i.getByteArrayExtra(GROUP_ID);
		if (b == null) throw new IllegalStateException("No group ID in intent");
		GroupId groupId = new GroupId(b);
		blogController.setGroupId(groupId);
		viewModel.setGroupId(groupId);

		setContentView(R.layout.activity_fragment_container_toolbar);
		Toolbar toolbar = setUpCustomToolbar(false);

		// Open Sharing Status on Toolbar click
		if (toolbar != null) {
			toolbar.setOnClickListener(v -> {
				Intent i1 = new Intent(BlogActivity.this,
						BlogSharingStatusActivity.class);
				i1.putExtra(GROUP_ID, groupId.getBytes());
				startActivity(i1);
			});
		}

		if (state == null) {
			showInitialFragment(BlogFragment.newInstance(groupId));
		}
	}

}
