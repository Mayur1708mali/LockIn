# Use lightweight official Node Alpine image
FROM node:20-alpine

# Set working directory inside container
WORKDIR /usr/src/app

# Copy package configurations
COPY package*.json ./

# Install only production dependencies
RUN npm ci --only=production

# Copy the rest of the server code
COPY . .

# Expose server PORT (Render/Railway bind this automatically)
EXPOSE 3000

# Set environment to production
ENV NODE_ENV=production

# Boot the express server
CMD ["node", "src/index.js"]
