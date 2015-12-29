package com.android.msahakyan.nestedrecycler.adapter;

import android.content.Context;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.android.msahakyan.nestedrecycler.R;
import com.android.msahakyan.nestedrecycler.application.AppController;
import com.android.msahakyan.nestedrecycler.common.Helper;
import com.android.msahakyan.nestedrecycler.common.ItemClickListener;
import com.android.msahakyan.nestedrecycler.model.Movie;
import com.android.msahakyan.nestedrecycler.model.MovieListParser;
import com.android.msahakyan.nestedrecycler.model.RecyclerItem;
import com.android.msahakyan.nestedrecycler.model.RelatedMoviesItem;
import com.android.msahakyan.nestedrecycler.net.NetworkRequestListener;
import com.android.msahakyan.nestedrecycler.net.NetworkUtilsImpl;
import com.android.msahakyan.nestedrecycler.view.FadeInNetworkImageView;
import com.android.volley.Request;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.github.rahatarmanahmed.cpv.CircularProgressView;
import com.google.gson.Gson;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import butterknife.Bind;
import butterknife.ButterKnife;

/**
 * @author msahakyan
 *         <p/>
 *         Movie adapter which actually is adapter of parent recycler view's
 */
public class MovieAdapter extends RecyclerView.Adapter<MovieAdapter.GenericViewHolder> {

    private static final int TYPE_MOVIE = 1001;
    private static final int TYPE_RELATED_ITEMS = 1002;

    private Context mContext;
    private List<RecyclerItem> mItemList;
    private int relatedItemsPosition = RecyclerView.NO_POSITION;
    private List<Integer> mLastItemGenreIds;
    private RecyclerView mRecyclerView;
    private String mEndpoint;
    private Map<String, String> mUrlParams;
    private ImageLoader mImageLoader = AppController.getInstance().getImageLoader();

    public MovieAdapter(Context context, List<RecyclerItem> itemList) {
        mContext = context;
        mItemList = itemList;
    }

    @Override
    public GenericViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        View view;
        GenericViewHolder customItemViewHolder = null;
        switch (viewType) {
            case TYPE_MOVIE:
                view = LayoutInflater.from(mContext).inflate(R.layout.layout_movie_item, parent, false);
                customItemViewHolder = new MovieViewHolder(view);
                break;
            case TYPE_RELATED_ITEMS:
                view = LayoutInflater.from(mContext).inflate(R.layout.layout_movie_related_items, parent, false);
                customItemViewHolder = new RelatedMoviesViewHolder(view);
                break;
            default:
                break;
        }

        return customItemViewHolder;
    }

    @Override
    public void onBindViewHolder(GenericViewHolder holder, int position) {
        if (holder instanceof MovieViewHolder) {
            bindMovieViewHolder((MovieViewHolder) holder, (Movie) mItemList.get(position));
        } else if (holder instanceof RelatedMoviesViewHolder) {
            bindRelatedItemsViewHolder((RelatedMoviesViewHolder) holder);
        }
    }

    private void bindMovieViewHolder(MovieViewHolder holder, final Movie movie) {
        holder.name.setText(movie.getTitle());
        if (movie.getPosterPath() != null) {
            String fullPosterPath = "http://image.tmdb.org/t/p/w500/" + movie.getPosterPath();
            holder.thumbnail.setImageUrl(fullPosterPath, mImageLoader);
        }
        holder.date.setText(movie.getReleaseDate());
        holder.setClickListener(new ItemClickListener() {
            @Override
            public void onClick(View view, int position) {
                final RelatedMoviesItem relatedMoviesItem = new RelatedMoviesItem();
                mLastItemGenreIds = movie.getGenreIds();

                // If related item was added before, we have to remove it and add a new one
                if (relatedItemsPosition != -1) {
                    if (position > relatedItemsPosition) {
                        position--;
                    }

                    mItemList.remove(relatedItemsPosition);
                    notifyItemRemoved(relatedItemsPosition);
                    notifyItemRangeChanged(relatedItemsPosition, mItemList.size());
                }

                if (Helper.isEmpty(mLastItemGenreIds)) {
                    Toast.makeText(mContext, R.string.no_related_movies_available, Toast.LENGTH_SHORT).show();
                    relatedItemsPosition = RecyclerView.NO_POSITION;
                    return;
                }

                if (position < mItemList.size() - 1) {
                    relatedItemsPosition = position % 2 == 0 ? position + 2 : position + 1;
                } else {
                    relatedItemsPosition = position + 1;
                }

                mItemList.add(relatedItemsPosition, relatedMoviesItem);
                notifyItemInserted(relatedItemsPosition);
                notifyItemRangeChanged(relatedItemsPosition, mItemList.size());
                if (mRecyclerView != null && relatedItemsPosition > 1) {
                    ((GridLayoutManager) mRecyclerView.getLayoutManager()).scrollToPositionWithOffset(relatedItemsPosition - 1, 0);
                }
            }
        });
    }

    private void bindRelatedItemsViewHolder(final RelatedMoviesViewHolder holder) {
        LinearLayoutManager layoutManager
            = new LinearLayoutManager(mContext, LinearLayoutManager.HORIZONTAL, false);
        holder.relatedItemsRecyclerView.setAdapter(null); // clear old data
        holder.relatedMoviesHeader.setText(mContext.getString(R.string.loading_data));
        holder.relatedItemsRecyclerView.setLayoutManager(layoutManager);
        holder.relatedItemsRecyclerView.setHasFixedSize(true);

        holder.progressView.setVisibility(View.VISIBLE);
        holder.progressView.startAnimation();

        // Loading similar products
        initEndpointAndUrlParams(1);
        loadRelatedMovies(holder);
    }

    private void loadRelatedMovies(final RelatedMoviesViewHolder holder) {
        new NetworkUtilsImpl().executeJsonRequest(Request.Method.GET, new StringBuilder(mEndpoint),
            mUrlParams, new NetworkRequestListener() {
                @Override
                public void onSuccess(JSONObject jsonResponse) {
                    holder.progressView.clearAnimation();
                    holder.progressView.setVisibility(View.GONE);
                    List<Movie> movieList = new Gson().fromJson(jsonResponse.toString(), MovieListParser.class).getResults();
                    holder.relatedItemsRecyclerView.setAdapter(new RelatedMoviesAdapter(mContext, movieList));
                    holder.relatedMoviesHeader.setText(mContext.getString(R.string.related_movies));
                }

                @Override
                public void onError(VolleyError error) {
                    holder.progressView.clearAnimation();
                    holder.progressView.setVisibility(View.GONE);
                    Toast.makeText(mContext, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void initEndpointAndUrlParams(int page) {
        mEndpoint = "http://api.themoviedb.org/3/discover/movie";
        mUrlParams = new HashMap<>();
        mUrlParams.put("api_key", "746bcc0040f68b8af9d569f27443901f");
        mUrlParams.put("sort_by", "vote_average.desc");
        mUrlParams.put("with_genres", Helper.getCsvGenreIds(mLastItemGenreIds));
        mUrlParams.put("page", String.valueOf(page));
    }

    @Override
    public int getItemCount() {
        return mItemList.size();
    }

    @Override
    public int getItemViewType(int position) {

        int type = super.getItemViewType(position);

        if (mItemList.get(position) instanceof Movie) {
            type = TYPE_MOVIE;
        } else if (mItemList.get(position) instanceof RelatedMoviesItem) {
            type = TYPE_RELATED_ITEMS;
        }

        return type;
    }

    static class GenericViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private ItemClickListener clickListener;

        public GenericViewHolder(View itemView) {
            super(itemView);
            itemView.setOnClickListener(this);
        }

        public void setClickListener(ItemClickListener itemClickListener) {
            this.clickListener = itemClickListener;
        }

        @Override
        public void onClick(View v) {
            this.clickListener.onClick(v, getPosition());
        }
    }

    /**
     * ViewHolder of the movie element
     */
    class MovieViewHolder extends GenericViewHolder {

        @Bind(R.id.movie_name)
        protected TextView name;

        @Bind(R.id.movie_thumbnail)
        protected FadeInNetworkImageView thumbnail;

        @Bind(R.id.movie_production_date)
        protected TextView date;

        @Bind(R.id.movie_type)
        protected TextView type;

        public MovieViewHolder(View view) {
            super(view);
            ButterKnife.bind(this, view);
        }
    }

    /**
     * ViewHolder of the related movies element which contains reference
     * to related movies recyclerView and textView header of that element
     */
    class RelatedMoviesViewHolder extends GenericViewHolder {

        @Bind(R.id.related_movies_recycler)
        protected RecyclerView relatedItemsRecyclerView;

        @Bind(R.id.related_movies_header)
        protected TextView relatedMoviesHeader;

        @Bind(R.id.progress_view)
        protected CircularProgressView progressView;

        public RelatedMoviesViewHolder(View view) {
            super(view);
            ButterKnife.bind(this, view);
        }

        @Override
        public void onClick(View v) {
            // do nothing
        }
    }

    // Overriding this method to get access to recyclerView on which current MovieAdapter has been attached.
    // In the future we will use that reference for scrolling to newly added relatedMovies item
    @Override
    public void onAttachedToRecyclerView(RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        mRecyclerView = recyclerView;
    }
}
